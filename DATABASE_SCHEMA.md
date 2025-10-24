# Схема базы данных

## Booking Service

### Таблица `users`
```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);
```

**Индексы:**
- `idx_user_username` - для быстрого поиска по username при авторизации

### Таблица `bookings`
```sql
CREATE TABLE bookings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    request_id VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Индексы:**
- `idx_booking_user_id` - для быстрого поиска бронирований пользователя
- `idx_booking_room_id` - для поиска бронирований номера
- `idx_booking_status` - для фильтрации по статусу
- `idx_booking_request_id` - для проверки идемпотентности
- `idx_booking_dates` - составной индекс для проверки конфликтов дат

## Hotel Management Service

### Таблица `hotels`
```sql
CREATE TABLE hotels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL
);
```

### Таблица `rooms`
```sql
CREATE TABLE rooms (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    hotel_id BIGINT NOT NULL,
    number VARCHAR(50) NOT NULL,
    available BOOLEAN NOT NULL,
    times_booked INT NOT NULL DEFAULT 0,
    FOREIGN KEY (hotel_id) REFERENCES hotels(id)
);
```

**Индексы:**
- `idx_room_hotel_id` - для поиска номеров отеля
- `idx_room_available` - для фильтрации доступных номеров
- `idx_room_times_booked` - для сортировки по загруженности
- `idx_room_number` - для поиска по номеру

### Таблица `room_availability`
```sql
CREATE TABLE room_availability (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    is_blocked BOOLEAN NOT NULL,
    booking_id VARCHAR(255),
    request_id VARCHAR(255),
    FOREIGN KEY (room_id) REFERENCES rooms(id)
);
```

**Индексы:**
- `idx_availability_room_id` - для поиска блокировок номера
- `idx_availability_dates` - составной индекс для проверки конфликтов
- `idx_availability_request_id` - для идемпотентности

## Связи между таблицами

1. **users → bookings** (1:N) - один пользователь может иметь много бронирований
2. **hotels → rooms** (1:N) - один отель может иметь много номеров
3. **rooms → room_availability** (1:N) - один номер может иметь много блокировок

## Оптимизации

- **Составные индексы** для сложных запросов (даты, статус)
- **Уникальные индексы** для предотвращения дубликатов
- **Внешние ключи** для обеспечения целостности данных
- **Партиционирование** по датам для больших объемов данных
