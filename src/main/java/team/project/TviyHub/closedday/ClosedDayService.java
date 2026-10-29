package team.project.TviyHub.closedday;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.closedday.dto.ClosedDayRequest;
import team.project.TviyHub.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class ClosedDayService {

    private final ClosedDayRepository closedDayRepository;
    private final ClosedDayMapper closedDayMapper;

    @Transactional(readOnly = true)
    public List<ClosedDay> getAllClosedDays() {
        return closedDayRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ClosedDay getClosedDayByDate(LocalDate date) {
        return closedDayRepository.findById(date)
                .orElseThrow(() -> new BusinessException("Day " + date + " is not marked as closed"));
    }

    @Transactional(readOnly = true)
    public boolean isDateClosed(LocalDate date) {
        return closedDayRepository.existsByDate(date);
    }

    @Transactional
    public ClosedDay createClosedDay(ClosedDayRequest request) {
        if (closedDayRepository.existsById(request.date())) {
            throw new BusinessException("Date " + request.date() + " is already marked as closed");
        }
        ClosedDay closedDay = closedDayMapper.toEntity(request);
        return closedDayRepository.save(closedDay);
    }

    @Transactional
    public ClosedDay updateClosedDay(LocalDate date, ClosedDayRequest request) {
        ClosedDay closedDay = getClosedDayByDate(date);
        closedDayMapper.updateEntityFromDto(request, closedDay);
        return closedDayRepository.save(closedDay);
    }

    @Transactional
    public void deleteClosedDay(LocalDate date) {
        ClosedDay closedDay = getClosedDayByDate(date);
        closedDayRepository.delete(closedDay);
    }
}
