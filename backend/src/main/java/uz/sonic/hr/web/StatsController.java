package uz.sonic.hr.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.security.CurrentMembership;
import uz.sonic.hr.service.StatsService;
import uz.sonic.hr.web.dto.Dtos.MonthlyStats;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final CurrentMembership currentMembership;

    @GetMapping("/monthly")
    public MonthlyStats monthly(@RequestParam(required = false) Integer year,
                                @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        return statsService.monthly(
                currentMembership.get(),
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue());
    }
}
