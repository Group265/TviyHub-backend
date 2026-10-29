package team.project.TviyHub.closedday;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "closed_days")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClosedDay {

    @Id
    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 255)
    private String reason;
}
