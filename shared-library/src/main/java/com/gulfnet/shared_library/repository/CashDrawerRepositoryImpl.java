package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CashDrawer;
import com.gulfnet.shared_library.entity.CashDrawerTranslation;
import com.gulfnet.shared_library.enums.EntityStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class CashDrawerRepositoryImpl implements CashDrawerRepositoryCustom {

    private static final String LANG_EN = "en";

    @PersistenceContext
    private EntityManager em;

    /**
     * Returns a page of cash drawers for a restaurant, restricted to drawers that have an English
     * ({@code en}) translation. Optionally filters by {@link EntityStatus} and a case-insensitive
     * search on {@code serialNumber} or any linked {@link CashDrawerTranslation} name. Sorting is applied
     * via {@link #applyOrderBy}; the total element count uses the same filter predicate set as
     * {@link #executeCountQuery}.
     *
     * @param restaurantId restaurant scope
     * @param status       optional status filter; {@code null} means any status
     * @param search       optional substring match on serial or translated name
     * @param pageable     paging (offset/limit); unpaged runs the data query without {@code setFirstResult}/{@code setMaxResults}
     * @param sortField    logical field: {@code name}, {@code serialNumber}, {@code status}, {@code createdAt}, {@code updatedAt}, or other for default English-name ascending (ignores {@code direction})
     * @param direction    ascending or descending for recognized {@code sortField} values
     * @return page of {@link CashDrawer} entities and matching total count
     */
    @Override
    public Page<CashDrawer> findByRestaurantIdWithFilters(
            UUID restaurantId,
            EntityStatus status,
            String search,
            Pageable pageable,
            String sortField,
            Sort.Direction direction) {

        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<CashDrawer> cq = cb.createQuery(CashDrawer.class);
        Root<CashDrawer> root = cq.from(CashDrawer.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("restaurant").get("id"), restaurantId));
        predicates.add(existsEnglishTranslation(cq, cb, root));

        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (search != null && !search.trim().isEmpty()) {
            String term = "%" + search.trim().toLowerCase() + "%";
            Predicate serialMatch = cb.like(cb.lower(root.get("serialNumber")), term);
            Subquery<Long> nameSub = cq.subquery(Long.class);
            Root<CashDrawerTranslation> st = nameSub.from(CashDrawerTranslation.class);
            nameSub.select(cb.literal(1L));
            nameSub.where(
                    cb.equal(st.get("cashDrawer"), root),
                    cb.like(cb.lower(st.get("name")), term));
            predicates.add(cb.or(serialMatch, cb.exists(nameSub)));
        }
        cq.where(predicates.toArray(Predicate[]::new));
        cq.distinct(false);

        applyOrderBy(cb, cq, root, sortField, direction);

        TypedQuery<CashDrawer> query = em.createQuery(cq);
        if (pageable.isPaged()) {
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
        }
        List<CashDrawer> content = query.getResultList();

        long total = executeCountQuery(cb, restaurantId, status, search);

        return new PageImpl<>(content, pageable, total);
    }

    private static Predicate existsEnglishTranslation(
            CriteriaQuery<?> cq, CriteriaBuilder cb, Root<CashDrawer> root) {
        Subquery<Long> hasEn = cq.subquery(Long.class);
        Root<CashDrawerTranslation> t = hasEn.from(CashDrawerTranslation.class);
        hasEn.select(cb.literal(1L));
        hasEn.where(
                cb.equal(t.get("cashDrawer"), root),
                cb.equal(t.get("languageCode"), LANG_EN));
        return cb.exists(hasEn);
    }

    /**
     * Appends {@code ORDER BY} clauses for the cash-drawer listing query. {@code name} (and the default
     * when {@code sortField} is unrecognized) uses the English translation's {@code name}; other
     * supported fields order on root entity properties.
     *
     * @param cb         criteria builder
     * @param cq         cash drawer query being ordered
     * @param root       cash drawer root
     * @param sortField  see {@link #findByRestaurantIdWithFilters}
     * @param direction  sort direction when {@code sortField} is recognized; unknown {@code sortField} is ordered by English name ascending only
     */
    private void applyOrderBy(
            CriteriaBuilder cb,
            CriteriaQuery<CashDrawer> cq,
            Root<CashDrawer> root,
            String sortField,
            Sort.Direction direction) {

        boolean desc = direction == Sort.Direction.DESC;
        List<Order> orders = new ArrayList<>();
        if ("name".equalsIgnoreCase(sortField)) {
            Subquery<String> nameOrder = cq.subquery(String.class);
            Root<CashDrawerTranslation> t = nameOrder.from(CashDrawerTranslation.class);
            nameOrder.select(t.get("name"));
            nameOrder.where(
                    cb.equal(t.get("cashDrawer"), root),
                    cb.equal(t.get("languageCode"), LANG_EN));
            orders.add(desc ? cb.desc(nameOrder) : cb.asc(nameOrder));
        } else if ("serialNumber".equalsIgnoreCase(sortField)) {
            orders.add(desc ? cb.desc(root.get("serialNumber")) : cb.asc(root.get("serialNumber")));
        } else if ("status".equalsIgnoreCase(sortField)) {
            orders.add(desc ? cb.desc(root.get("status")) : cb.asc(root.get("status")));
        } else if ("createdAt".equalsIgnoreCase(sortField)) {
            orders.add(desc ? cb.desc(root.get("createdAt")) : cb.asc(root.get("createdAt")));
        } else if ("updatedAt".equalsIgnoreCase(sortField)) {
            orders.add(desc ? cb.desc(root.get("updatedAt")) : cb.asc(root.get("updatedAt")));
        } else {
            Subquery<String> nameOrder = cq.subquery(String.class);
            Root<CashDrawerTranslation> t = nameOrder.from(CashDrawerTranslation.class);
            nameOrder.select(t.get("name"));
            nameOrder.where(
                    cb.equal(t.get("cashDrawer"), root),
                    cb.equal(t.get("languageCode"), LANG_EN));
            orders.add(cb.asc(nameOrder));
        }
        cq.orderBy(orders);
    }

    /**
     * Runs a {@code COUNT} query using the same restaurant, English-translation, status, and search
     * predicates as {@link #findByRestaurantIdWithFilters} so {@link PageImpl} totals stay consistent
     * with the page content query.
     *
     * @param cb            criteria builder
     * @param restaurantId  restaurant scope
     * @param status        optional status filter
     * @param search        optional search term (same semantics as the page query)
     * @return number of matching cash drawers
     */
    private long executeCountQuery(CriteriaBuilder cb, UUID restaurantId, EntityStatus status, String search) {
        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<CashDrawer> countRoot = countQ.from(CashDrawer.class);
        List<Predicate> countPreds = new ArrayList<>();
        countPreds.add(cb.equal(countRoot.get("restaurant").get("id"), restaurantId));
        countPreds.add(existsEnglishTranslation(countQ, cb, countRoot));
        if (status != null) {
            countPreds.add(cb.equal(countRoot.get("status"), status));
        }
        if (search != null && !search.trim().isEmpty()) {
            String term = "%" + search.trim().toLowerCase() + "%";
            Predicate serialMatch = cb.like(cb.lower(countRoot.get("serialNumber")), term);
            Subquery<Long> nameSub = countQ.subquery(Long.class);
            Root<CashDrawerTranslation> st = nameSub.from(CashDrawerTranslation.class);
            nameSub.select(cb.literal(1L));
            nameSub.where(
                    cb.equal(st.get("cashDrawer"), countRoot),
                    cb.like(cb.lower(st.get("name")), term));
            countPreds.add(cb.or(serialMatch, cb.exists(nameSub)));
        }
        countQ.select(cb.count(countRoot));
        countQ.where(countPreds.toArray(Predicate[]::new));
        return em.createQuery(countQ).getSingleResult();
    }

    /**
     * Returns all {@link EntityStatus#ACTIVE} cash drawers for a restaurant that have an English
     * translation, ordered ascending by the English display name.
     *
     * @param restaurantId restaurant scope
     * @return list (possibly empty), never {@code null}
     */
    @Override
    public List<CashDrawer> findActiveDrawersByRestaurantIdOrderByEnglishName(UUID restaurantId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<CashDrawer> cq = cb.createQuery(CashDrawer.class);
        Root<CashDrawer> root = cq.from(CashDrawer.class);
        cq.where(
                cb.equal(root.get("restaurant").get("id"), restaurantId),
                cb.equal(root.get("status"), EntityStatus.ACTIVE),
                existsEnglishTranslation(cq, cb, root));
        cq.distinct(false);

        Subquery<String> nameOrder = cq.subquery(String.class);
        Root<CashDrawerTranslation> t = nameOrder.from(CashDrawerTranslation.class);
        nameOrder.select(t.get("name"));
        nameOrder.where(
                cb.equal(t.get("cashDrawer"), root),
                cb.equal(t.get("languageCode"), LANG_EN));
        cq.orderBy(cb.asc(nameOrder));

        return em.createQuery(cq).getResultList();
    }
}
