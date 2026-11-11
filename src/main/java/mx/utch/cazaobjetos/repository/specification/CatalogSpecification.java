package mx.utch.cazaobjetos.repository.specification;

import java.time.LocalDate;
import java.util.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import mx.utch.cazaobjetos.dto.CatalogQueryDto;

/** All property names are server constants. User values are parameterized, with LIKE wildcards escaped. */
public final class CatalogSpecification {
    private CatalogSpecification() {}

    public static <T> Specification<T> matching(CatalogQueryDto query, String locationField,
                                                String dateField, Enum<?> status) {
        return (root, criteria, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (!query.q().isEmpty()) {
                String text = literalContains(query.q());
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("title")), text, '!'),
                        cb.like(cb.lower(root.get("description")), text, '!'),
                        cb.like(cb.lower(root.get(locationField)), text, '!')));
            }
            if (!query.location().isEmpty()) {
                conditions.add(cb.like(cb.lower(root.get(locationField)), literalContains(query.location()), '!'));
            }
            if (query.category() != null) conditions.add(cb.equal(root.get("category"), query.category()));
            if (status != null) conditions.add(cb.equal(root.get("status"), status));
            if (query.custody() != null) conditions.add(cb.equal(root.get("custody"), query.custody()));
            if (query.dateFrom() != null) conditions.add(cb.greaterThanOrEqualTo(root.<LocalDate>get(dateField), query.dateFrom()));
            if (query.dateTo() != null) conditions.add(cb.lessThanOrEqualTo(root.<LocalDate>get(dateField), query.dateTo()));
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
    private static String literalContains(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
    }
}
