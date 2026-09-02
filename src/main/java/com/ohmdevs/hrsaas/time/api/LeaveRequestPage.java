package com.ohmdevs.hrsaas.time.api;

import java.util.List;

/** Una página de {@link LeaveRequestView}; {@code nextAfterId} nulo significa que no hay más — ver {@code payroll.api.PayslipPage}, mismo patrón. */
public record LeaveRequestPage(List<LeaveRequestView> items, Long nextAfterId) {
}
