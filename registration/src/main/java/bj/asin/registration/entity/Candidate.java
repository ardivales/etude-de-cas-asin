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
import java.time.LocalDate;

@Entity
@Table(name = "candidats")
@Getter
@NoArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String npi;

    @Column(name = "nom")
    private String lastName;

    @Column(name = "prenoms")
    private String firstNames;

    @Column(name = "date_naissance")
    private LocalDate birthDate;

    @Column(name = "cree_le")
    private Instant createdAt;

    public Candidate(String npi, String lastName, String firstNames, LocalDate birthDate, Instant createdAt) {
        this.npi = npi;
        this.lastName = lastName;
        this.firstNames = firstNames;
        this.birthDate = birthDate;
        this.createdAt = createdAt;
    }
}
