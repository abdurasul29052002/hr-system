package uz.sonic.hr.task;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.sonic.hr.common.security.CurrentMembership;
import uz.sonic.hr.common.dto.Dtos.MemberActivityDto;
import uz.sonic.hr.common.dto.Dtos.MonthlyStats;
import uz.sonic.hr.common.dto.Dtos.TimelineTaskDto;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final MonthlyReportService reportService;
    private final CurrentMembership currentMembership;

    @GetMapping("/monthly")
    public MonthlyStats monthly(@RequestParam(required = false) Integer year,
                                @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        return statsService.monthly(currentMembership.get(),
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue());
    }

    /** Live snapshot of who is working on what right now. */
    @GetMapping("/current")
    public List<MemberActivityDto> current() {
        return statsService.current(currentMembership.get());
    }

    /** Monthly Gantt timeline: each task's taken → completed span within the month. */
    @GetMapping("/timeline")
    public List<TimelineTaskDto> timeline(@RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        return statsService.timeline(currentMembership.get(),
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue());
    }

    /** Monthly report as a downloadable PDF (leader/manager). */
    @GetMapping("/report")
    public ResponseEntity<byte[]> report(@RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        byte[] pdf = reportService.monthlyReport(currentMembership.get(), y, m);
        String filename = "report-" + y + "-" + String.format("%02d", m) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
