package bj.asin.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "centres")
@Getter
@NoArgsConstructor
public class Center {

    @Id
    private Integer id;

    private String code;

    @Column(name = "nom")
    private String name;

    @Column(name = "capacite")
    private int capacity;

    @Column(name = "dernier_numero")
    private int lastTableNumber;

    public String assignNextTableNumber() {
        lastTableNumber++;
        return "%s-%04d".formatted(code, lastTableNumber);
    }
}
