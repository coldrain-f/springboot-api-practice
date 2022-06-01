package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class OrderApiControllerNew {

    private final OrderRepository orderRepository;

    @GetMapping("/new/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        all.forEach(o -> {
            o.getMember().getName(); // Lazy 초기화
            o.getDelivery().getAddress(); // Lazy 초기화
            // orderItem 에도 item 이 있기 때문에 Lazy 초기화를 해줘야 한다.
            // Order 와 OrderItem 은 1:N
            List<OrderItem> orderItems = o.getOrderItems();
            orderItems.forEach(i -> i.getItem().getName());
        });
        return all;
    }

    @GetMapping("/new/v2/orders")
    public List<OrderDtoNew> ordersV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        return orders.stream()
                .map(o -> new OrderDtoNew(o))
                .collect(Collectors.toList());
    }

    @GetMapping("/new/v3/orders")
    public List<OrderDtoNew> ordersV3() {
        List<Order> allWithItem = orderRepository.findAllWithItem();
    }

    @Getter // 없으면 no properties 예외 생김
    static class OrderDtoNew {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        private List<OrderItemDtoNew> orderItems; // dto안에 entity도 안 됨.. 이것도 dto로 변환..

        public OrderDtoNew(Order o) {
            this.orderId = o.getId();
            this.name = o.getMember().getName();
            this.orderDate = o.getOrderDate();
            this.orderStatus = o.getStatus();
            this.address = o.getDelivery().getAddress();
            orderItems = o.getOrderItems().stream()
                    .map(orderItem -> new OrderItemDtoNew(orderItem))
                    .collect(Collectors.toList());
        }
    }

    @Getter
    static class OrderItemDtoNew {
        private String itemName; // 상품 명
        private int orderPrice; // 주문 가격
        private int count; // 주문 수량

        public OrderItemDtoNew(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }
}