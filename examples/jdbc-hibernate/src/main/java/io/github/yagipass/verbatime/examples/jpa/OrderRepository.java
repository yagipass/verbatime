package io.github.yagipass.verbatime.examples.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findTop10ByOrderByIdDesc();
}
