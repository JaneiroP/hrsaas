package com.ohmdevs.hrsaas.platform.batch;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.platform.tenancy.PlatformContextTaskDecorator;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El test decisivo de la Pieza 4: confirma que
 * {@link PlatformContextTaskDecorator} propaga el tenant activo a cada hilo
 * de trabajo de un step particionado, no solo al hilo que lanza el job.
 *
 * <p><strong>El {@code Job} se construye a mano dentro del método de test,
 * nunca como {@code @Bean}.</strong> La primera versión lo exponía como bean
 * en {@link SmokeJobConfiguration} — y {@code JobLauncherApplicationRunner}
 * de Boot recoge <em>cualquier</em> bean {@code Job} del contexto y lo
 * autoejecuta al arrancar, con parámetros vacíos y sin tenant fijado
 * (ocurre antes de que el cuerpo del test corra). Ese autoarranque rompía con
 * {@code NullPointerException} en {@code ConcurrentHashMap.put} — no acepta
 * valores {@code null}, y sin tenant el valor a registrar es {@code null}.
 * Encontrado corriendo la suite completa, no en revisión de código.
 * {@code spring.batch.job.enabled=false} no existe en esta versión de Boot
 * (ver {@code BatchJobRepositoryConfiguration}), así que la única forma de
 * evitar el autoarranque es que el {@code Job} nunca sea un bean: los
 * componentes que sí lo son ({@code Partitioner}, el {@code TaskExecutor}
 * decorado, la sonda) no son de tipo {@code Job}, así que el runner nunca los
 * toca.
 *
 * <p>Extiende {@link AbstractPostgresTest} para reutilizar el mismo contexto
 * de Spring cacheado que el resto de la suite (patrón "singleton container"),
 * aunque este test en concreto no toca ninguna tabla de PostgreSQL — el
 * {@code JobRepository} de Batch vive por completo en el H2 dedicado de
 * {@link BatchJobRepositoryConfiguration}.
 */
@Import(TenantPropagationBatchTest.SmokeJobConfiguration.class)
class TenantPropagationBatchTest extends AbstractPostgresTest {

    private static final long PROBE_TENANT_ID = 991L;
    private static final int PARTITION_COUNT = 3;

    @Autowired
    private JobOperator jobOperator;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    @Qualifier("batchTransactionManager")
    private PlatformTransactionManager batchTransactionManager;
    @Autowired
    private Partitioner tenantPropagationPartitioner;
    @Autowired
    private TaskExecutor partitionTaskExecutor;
    @Autowired
    private TenantPropagationProbe probe;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("cada partición ve, en su propio hilo, el tenant activo en el hilo que lanzó el job")
    void tenantPropagatesToEachPartitionWorkerThread() throws Exception {
        TenantContext.set(PROBE_TENANT_ID);

        Tasklet tasklet = (contribution, chunkContext) -> {
            probe.record(chunkContext.getStepContext().getStepName());
            return RepeatStatus.FINISHED;
        };
        Step workerStep = new StepBuilder("tenantPropagationWorkerStep", jobRepository)
                .tasklet(tasklet, batchTransactionManager)
                .build();
        Step masterStep = new StepBuilder("tenantPropagationMasterStep", jobRepository)
                .partitioner("tenantPropagationWorkerStep", tenantPropagationPartitioner)
                .step(workerStep)
                .gridSize(PARTITION_COUNT)
                .taskExecutor(partitionTaskExecutor)
                .build();
        Job job = new JobBuilder("tenantPropagationSmokeJob", jobRepository)
                .start(masterStep)
                .build();

        JobParameters parameters = new JobParametersBuilder()
                .addLong("probeRunId", System.nanoTime())
                .toJobParameters();

        JobExecution execution = jobOperator.run(job, parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(probe.recordedPartitions()).hasSize(PARTITION_COUNT);
        probe.recordedPartitions().forEach(partitionStepName ->
                assertThat(probe.tenantSeenBy(partitionStepName))
                        .as("partición %s debió ver el tenant del hilo que lanzó el job", partitionStepName)
                        .isEqualTo(PROBE_TENANT_ID));
    }

    @TestConfiguration
    static class SmokeJobConfiguration {

        @Bean
        TenantPropagationProbe tenantPropagationProbe() {
            return new TenantPropagationProbe();
        }

        /**
         * Deliberadamente no es el {@code applicationTaskExecutor}
         * autoconfigurado: un {@code TaskExecutor} propio para el
         * {@code PartitionHandler}, decorado a mano, deja explícito que la
         * propagación de contexto no es gratis — hay que acordarse de
         * pedirla.
         */
        @Bean
        TaskExecutor partitionTaskExecutor(PlatformContextTaskDecorator taskDecorator) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(PARTITION_COUNT);
            executor.setMaxPoolSize(PARTITION_COUNT);
            executor.setThreadNamePrefix("batch-tenant-probe-");
            executor.setTaskDecorator(taskDecorator);
            executor.initialize();
            return executor;
        }

        @Bean
        Partitioner tenantPropagationPartitioner() {
            return gridSize -> {
                Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
                for (int i = 0; i < gridSize; i++) {
                    ExecutionContext context = new ExecutionContext();
                    context.putInt("partitionIndex", i);
                    partitions.put("partition" + i, context);
                }
                return partitions;
            };
        }
    }

    /** Registro concurrente de qué tenant vio cada partición, en su propio hilo. */
    static class TenantPropagationProbe {

        private final ConcurrentHashMap<String, Long> tenantSeenByPartition = new ConcurrentHashMap<>();

        void record(String partitionStepName) {
            Long tenantId = TenantContext.current();
            tenantSeenByPartition.put(partitionStepName, tenantId);
        }

        Long tenantSeenBy(String partitionStepName) {
            return tenantSeenByPartition.get(partitionStepName);
        }

        Set<String> recordedPartitions() {
            return Set.copyOf(tenantSeenByPartition.keySet());
        }
    }
}
