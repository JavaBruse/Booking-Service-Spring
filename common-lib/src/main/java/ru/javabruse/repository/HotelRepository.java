package ru.javabruse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.javabruse.entity.Hotel;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {
}
