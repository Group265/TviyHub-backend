package team.project.TviyHub.tariff;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.project.TviyHub.exception.BusinessException;
import team.project.TviyHub.tariff.dto.TariffRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TariffService {

    private final TariffRepository tariffRepository;
    private final TariffMapper tariffMapper;

    @Transactional(readOnly = true)
    public List<Tariff> getAllActiveTariffs() {
        return tariffRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Tariff getTariffById(Long id) {
        return tariffRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Tariff with id: " + id + " not found"));
    }

    @Transactional
    public Tariff createTariff(TariffRequest request) {
        tariffRepository.findByWorkspaceTypeAndBookingType(request.workspaceType(), request.bookingType())
                .ifPresent(t -> {
                    throw new BusinessException("Tariff for this workspace and booking type already exists");
                });
        Tariff tariff = tariffMapper.toEntity(request);
        return tariffRepository.save(tariff);
    }

    @Transactional
    public Tariff updateTariff(Long id, TariffRequest request) {
        Tariff tariff = getTariffById(id);

        tariffRepository.findByWorkspaceTypeAndBookingType(request.workspaceType(), request.bookingType())
                .filter(t -> !t.getId().equals(id))
                .ifPresent(t -> {
                    throw new BusinessException("Another tariff for this workspace and booking type already exists");
                });

        tariffMapper.updateEntityFromDto(request, tariff);
        return tariffRepository.save(tariff);
    }

    @Transactional
    public void deleteTariff(Long id) {
        Tariff tariff = getTariffById(id);
        tariffRepository.delete(tariff);
    }
}
