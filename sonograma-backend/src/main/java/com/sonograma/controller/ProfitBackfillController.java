package com.sonograma.controller;

import com.sonograma.service.ProfitBackfillReport;
import com.sonograma.service.ProfitBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ventas/profit-backfill")
@RequiredArgsConstructor
public class ProfitBackfillController {
    private final ProfitBackfillService service;

    /** Dry-run by default. Pass execute=true only after reviewing the report. */
    @PostMapping
    public ResponseEntity<ProfitBackfillReport> run(
            @RequestParam(value = "execute", defaultValue = "false") boolean execute) {
        return ResponseEntity.ok(service.run(execute));
    }
}
