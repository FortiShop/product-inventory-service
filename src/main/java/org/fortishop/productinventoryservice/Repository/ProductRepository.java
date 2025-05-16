package org.fortishop.productinventoryservice.Repository;

import java.util.List;
import org.fortishop.productinventoryservice.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findAllByIsActiveTrue(Pageable pageable);

    List<Product> findByCategoryAndIsActiveTrue(String category);
}
