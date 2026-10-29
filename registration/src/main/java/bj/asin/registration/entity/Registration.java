package bj.asin.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "inscriptions")
@Getter
@NoArgsConstructor
public class Registration {

    public static final String VALIDATED = "validee";
    public static final String PENDING_VERIFICATION = "en_attente_verification";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "concours_id")
    private Integer examId;

    @Column(name = "centre_id")
    private Integer centerId;

    private String npi;

    @Column(name = "numero_table")
    private String tableNumber;

    @Column(name = "statut")
    private String status;

    @Column(name = "cree_le")
    private Instant createdAt;

    public Registration(Integer examId, Integer centerId, String npi, String tableNumber, String status,
                        Instant createdAt) {
        this.examId = examId;
        this.centerId = centerId;
        this.npi = npi;
        this.tableNumber = tableNumber;
        this.status = status;
        this.createdAt = createdAt;
    }
}
