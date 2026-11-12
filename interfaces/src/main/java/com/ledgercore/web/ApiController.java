package com.ledgercore.web;

import com.ledgercore.app.dto.ApiCommand;
import com.ledgercore.app.service.*;
import com.ledgercore.common.ApiResponse;
import com.ledgercore.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 演示控制台 REST API：所有操作都能在页面上手动触发。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final QueryService queries;
    private final PaymentService payments;
    private final ReconService recon;
    private final SettlementService settlements;
    private final SeedService seeds;
    private final ResetService resetService;
    private final RiskService risks;

    private ApiCommand cmd(ApiCommand c) {
        return c == null ? new ApiCommand() : c;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(queries.summary());
    }

    @GetMapping("/accounts")
    public ApiResponse<List<Account>> accounts() {
        return ApiResponse.ok(queries.accounts());
    }

    @GetMapping("/orders")
    public ApiResponse<List<PaymentOrder>> orders(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(queries.orders(limit));
    }

    @GetMapping("/entries")
    public ApiResponse<List<AccountEntry>> entries(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(queries.entries(limit));
    }

    @GetMapping("/vouchers")
    public ApiResponse<List<Voucher>> vouchers(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(queries.vouchers(limit));
    }

    @GetMapping("/vouchers/{voucherNo}")
    public ApiResponse<Map<String, Object>> voucher(@PathVariable String voucherNo) {
        return ApiResponse.ok(queries.voucherDetail(voucherNo));
    }

    @GetMapping("/bills")
    public ApiResponse<List<ChannelBill>> bills(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(queries.bills(limit));
    }

    @GetMapping("/channel-txns")
    public ApiResponse<List<ChannelTxn>> channelTxns(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(queries.channelTxns(limit));
    }

    @GetMapping("/splits")
    public ApiResponse<List<SplitDetail>> splits(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(queries.splits(limit));
    }

    @GetMapping("/refunds")
    public ApiResponse<List<RefundOrder>> refunds(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(queries.refunds(limit));
    }

    @GetMapping("/settlements")
    public ApiResponse<List<SettlementItem>> settlements(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(queries.settlements(limit));
    }

    @GetMapping("/diffs")
    public ApiResponse<List<ReconDiff>> diffs(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(queries.diffs(limit));
    }

    @GetMapping("/events")
    public ApiResponse<List<EventLog>> events(@RequestParam(defaultValue = "60") int limit) {
        return ApiResponse.ok(queries.events(limit));
    }

    @GetMapping("/events/after")
    public ApiResponse<List<EventLog>> eventsAfter(@RequestParam(defaultValue = "0") long afterId,
                                                  @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(queries.eventsAfter(afterId, limit));
    }

    @GetMapping("/rules")
    public ApiResponse<List<RiskRule>> rules() {
        return ApiResponse.ok(queries.rules());
    }

    @PostMapping("/payments")
    public ApiResponse<PaymentOrder> createPayment(@RequestBody(required = false) ApiCommand c) {
        return ApiResponse.ok(payments.create(cmd(c)));
    }

    @PostMapping("/payments/notify")
    public ApiResponse<PaymentOrder> notify(@RequestBody(required = false) ApiCommand c) {
        return ApiResponse.ok(payments.notify(cmd(c)));
    }

    @PostMapping("/payments/refund")
    public ApiResponse<RefundOrder> refund(@RequestBody(required = false) ApiCommand c) {
        return ApiResponse.ok(payments.refund(cmd(c)));
    }

    @PostMapping("/seed")
    public ApiResponse<Map<String, Object>> seed(@RequestBody(required = false) ApiCommand c) {
        return ApiResponse.ok(seeds.seed(cmd(c)));
    }

    @PostMapping("/anomaly")
    public ApiResponse<Map<String, Object>> anomaly(@RequestBody(required = false) ApiCommand c) {
        return ApiResponse.ok(seeds.inject(cmd(c)));
    }

    @PostMapping("/recon/run")
    public ApiResponse<Map<String, Object>> recon(@RequestBody(required = false) ApiCommand c) {
        return ApiResponse.ok(recon.run(cmd(c)));
    }

    @PostMapping("/recon/resolve")
    public ApiResponse<ReconDiff> resolve(@RequestBody(required = false) ApiCommand c) {
        return ApiResponse.ok(recon.resolve(cmd(c)));
    }

    @PostMapping("/settlements/run")
    public ApiResponse<Map<String, Object>> settle(@RequestBody(required = false) ApiCommand c) {
        return ApiResponse.ok(settlements.run(cmd(c)));
    }

    @PutMapping("/rules/{code}")
    public ApiResponse<RiskRule> updateRule(@PathVariable String code,
                                           @RequestBody(required = false) ApiCommand c) {
        ApiCommand command = cmd(c);
        return ApiResponse.ok(risks.update(code, command.getValue(), command.getEnabled()));
    }

    @PostMapping("/reset")
    public ApiResponse<Map<String, Object>> reset() {
        return ApiResponse.ok(resetService.reset());
    }
}
