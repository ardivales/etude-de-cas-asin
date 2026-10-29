package bj.asin.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "concours")
@Getter
@NoArgsConstructor
public class Exam {

    @Id
    private Integer id;

    private String code;

    @Column(name = "libelle")
    private String label;

    @Column(name = "date_epreuve")
    private LocalDate examDate;

    @Column(name = "cloture")
    private Instant closing;
}
