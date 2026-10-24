package com.foodieexpress.restaurantservice.repository;

import com.foodieexpress.restaurantservice.model.MenuItem;
import com.foodieexpress.restaurantservice.model.Restaurant;
import com.foodieexpress.restaurantservice.model.RestaurantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Repository;

@Repository
public class MongoRestaurantQueryRepository implements RestaurantQueryRepository {

    private final MongoTemplate mongoTemplate;

    public MongoRestaurantQueryRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Restaurant> discover(
            String searchText, String cuisine, RestaurantStatus status, Pageable pageable) {
        Query query = Query.query(Criteria.where("status").is(status));
        if (cuisine != null) {
            query.addCriteria(Criteria.where("cuisines").is(cuisine));
        }
        if (searchText != null) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matching(searchText));
        }

        long total = mongoTemplate.count(query, Restaurant.class);
        return new PageImpl<>(
                mongoTemplate.find(query.with(pageable), Restaurant.class),
                pageable,
                total);
    }

    @Override
    public Page<MenuItem> findMenuItems(String restaurantId, Boolean available, Pageable pageable) {
        Criteria criteria = Criteria.where("restaurantId").is(restaurantId);
        if (available != null) {
            criteria.and("available").is(available);
        }
        Query query = Query.query(criteria);
        long total = mongoTemplate.count(query, MenuItem.class);
        return new PageImpl<>(mongoTemplate.find(query.with(pageable), MenuItem.class), pageable, total);
    }
}
