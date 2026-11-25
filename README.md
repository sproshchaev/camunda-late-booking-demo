# camunda-late-booking-demo

[![Check](https://github.com/sproshchaev/camunda-late-booking-demo/actions/workflows/check.yml/badge.svg)](https://github.com/sproshchaev/camunda-late-booking-demo/actions/workflows/check.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Сергей Прощаев**

[![Website](https://img.shields.io/badge/prosoft.pages.dev-blue?logo=googlechrome&logoColor=white)](https://prosoft.pages.dev)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-sproshchaev-0A66C2)](https://www.linkedin.com/in/sproshchaev/)
[![Habr](https://img.shields.io/badge/Habr-sproshchaev-65A3BE?logo=habr&logoColor=white)](https://habr.com/ru/users/sproshchaev/)

---

## 1. Что это

Демо для статьи ["Процесс отменен, а внешняя бронь осталась"](https://habr.com/ru/companies/otus/articles/1101920/).
Camunda прерывает ожидание по таймеру, пока поставщик еще обрабатывает запрос на бронь. Тесты проверяют состояние процесса в движке и состояние брони у имитатора поставщика.

## Оглавление

- [Что внутри](#2-что-внутри)
- [Требования](#3-требования)
- [Быстрый старт](#4-быстрый-старт)
- [Использование](#5-использование)
- [Ограничения](#6-ограничения)
- [Лицензия](#7-лицензия)
- [Полезные ссылки](#8-полезные-ссылки)

## 2. Что внутри

- [booking.bpmn](src/test/resources/booking.bpmn) содержит задачу ожидания подтверждения, прерывающий таймер и два конечных события.
- [BookingProcessTest.java](src/test/java/booking/BookingProcessTest.java) запускает три сценария на Camunda и управляет порядком событий.
- [expected.tsv](expected.tsv) задает ожидаемые результаты. [run.sh](run.sh) сравнивает с ними фактический вывод тестов.

## 3. Требования

- JDK 25 и Maven 3.9.
- Запущенный Docker. Camunda Process Test скачивает образ Camunda 8.9.28 при первом запуске.

## 4. Быстрый старт

В корне клона выполнить:

```bash
./run.sh
```

Скрипт завершится с ошибкой, если тест упадет или результат разойдется с эталоном.

## 5. Использование

Тест запускает настоящую BPMN-модель и переводит часы движка вперед для сценариев с таймером. Поставщик хранит собственные состояния. Тест имитирует его подтверждение отдельно от команды `CompleteJob`.

| Сценарий | Конечное событие BPMN | Состояние у поставщика | CompleteJob | Отмены |
| --- | --- | --- | --- | ---: |
| Подтверждение до таймера | `booked` | `CONFIRMED` | `OK` | 0 |
| Позднее подтверждение | `timedOut` | `CONFIRMED` | `NOT_FOUND` | 0 |
| Позднее подтверждение с отменой | `timedOut` | `CANCELED` | `SKIPPED` | 1 |

В третьем сценарии обработчик сверяется с зарегистрированным тайм-аутом по идентификатору попытки. Повторное подтверждение не увеличивает число фактических отмен. `timedOut` означает отмену заявки по правилам процесса. Сам экземпляр в Camunda достигает конечного события и получает статус `COMPLETED`.

## 6. Ограничения

Поставщик и запись о тайм-ауте моделируются в памяти. Сценарии фиксируют порядок событий и проверяют исходы. Хранение состояния между перезапусками, одновременные события и сетевые сбои за пределами опыта.

## 7. Лицензия

[MIT](LICENSE).

## 8. Полезные ссылки

- [Статья на Хабре](https://habr.com/ru/companies/otus/articles/1101920/)
- [Прерывающий таймер](https://docs.camunda.io/docs/8.9/components/modeler/bpmn/timer-events/)
- [Управление часами в Camunda Process Test](https://docs.camunda.io/docs/8.9/apis-tools/testing/utilities/)
