package bj.asin.registration.service;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.LocalDate;

@UtilityClass
public class RegistrationRules {

    public static final int MAX_AGE = 35;

    public static int ageOnDecember31(LocalDate birthDate, int year) {
        return year - birthDate.getYear();
    }

    public static boolean isAgeAllowed(LocalDate birthDate, LocalDate examDate) {
        return ageOnDecember31(birthDate, examDate.getYear()) <= MAX_AGE;
    }

    public static boolean isRegistrationOpen(Instant now, Instant closing) {
        return !now.isAfter(closing);
    }
}
