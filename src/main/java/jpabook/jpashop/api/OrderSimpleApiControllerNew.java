package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.*;

/**
 * xToOne(ManyToOne, OneToOne) 에서의 성능 최적화
 * xToOne 시리즈는 기본이 즉시 로딩 ( fetch = EAGER )
 * xToMany 시리즈는 기본이 지연 로딩 ( fetch = LAZY )
 * 그래서 xToOne 시리즈는 모두 지연 로딩으로 변경해 주어야 한다.
 * Order
 * Order -> Member     ManyToOne
 * Order -> Delivery   OneToOne
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiControllerNew {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository; //의존관계 주입


    // TODO: 엔티티를 직접 노출
    @GetMapping("/new/v1/simple-orders")
    public List<Order> ordersV1() {
        // TODO: 문제점 1. 양방향인 경우 생기는 순환 참조
        // 보면 order 에는 member 가 있고
        // member 에는 orders 가 있어서 JSON 으로 반환하면 서로 무한으로 순환 참조한다.
        // 해결 방법 -> 한 쪽에 @JsonIgnore 를 붙여줘야 한다.
        // TODO: 문제점 2. 참조가 지연로딩(fetch = LAZY)인 경우
        // order 에 member 가 @ManyToOne(fetch = LAZY)으로 설정되어 있다.
        // 그런 상태에서 order 를 반환하면 member 는 실제 엔티티가 아니라 프록시 객체이므로 에러가 발생한다.
        // -> ByteBuddyInterceptor
        // 해결 방법 1. -> Hibernate5Module 로 강제 지연 로딩 사용
        // 해결 방법 2. -> 루프를 돌면서 프록시 초기화를 해준다.
        // return List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        // TODO: 문제점 3. API 스펙이 변경된다.
        // 회원과 배송 정보만 제공하고 싶어도 필요없는 모든 정보들도 다 노출된다. ( 다 가지고 와서 성능 문제도 있음 )
        // 엔티티가 변경되면 API 스펙 자체가 변경되어 API 를 쓰고 있던 사람들 모두 문제가 생길 수 있다.
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        orders.forEach(order -> {
            order.getMember().getName(); // LAZY 초기화
            order.getDelivery().getAddress(); // LAZY 초기화
        });
        return orders;
    }

    @GetMapping("/new/v2/simple-orders")
    public List<SimpleNewOrderDto> orderV2() {
        // TODO: 문제점 1. 엔티티를 DTO 로 변환시
        // v1이나 v2나 동일한 문제점이 하나 있다.
        // Member 와 Delivery 가 LAZY 로 설정되어 있어서 DTO 로 변환하면서
        // N + 1 문제가 발생한다. Order 만 조회했다고 생각했지만
        // 회원을 가져오는 select 쿼리 1번과 딜리버리를 가져오는 select 쿼리 1번이 각각 발생한다.
        // 해결 방법 1. -> 페치 조인을 사용해야 한다.

        // 여기서 1번 select 쿼리가 발생하여 ORDER 를 2개 조회 ( 조회 결과 수가 N이 된다. N = 2 )
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        // ORDER 2번을 순서대로 반복하면서 DTO 생성자 호출
        return orders.stream()
                .map(SimpleNewOrderDto::new)
                .collect(toList());
        // 1번 조회했다고 생각했지만 총 5번 조회됨..
    }

    @GetMapping("/new/v3/simple-orders")
    public List<SimpleNewOrderDto> ordersV3() {
        // fetch join 시 xToOne 관계는 별칭을 주어도 문제가 없지 않는다.
        // 하지만 xToMany 는 별칭을 주면 문제가 생기므로 주의해야한다.
        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        return orders.stream()
                .map(SimpleNewOrderDto::new)
                .collect(toList());
    }

    @Data
    static class SimpleNewOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleNewOrderDto(Order o) {
            this.orderId = o.getId();
            this.name = o.getMember().getName(); // 회원 조회 쿼리 1번 1번 총 2번 ( ORDER 수 만큼 )
            this.orderDate = o.getOrderDate();
            this.orderStatus = o.getStatus();
            this.address = o.getDelivery().getAddress(); // 딜리버리 조회 쿼리 1번 1번 총 2번 ( ORDER 수 만큼)
            // 하지만 조회 했을 때 이미 영속성 컨텍스트에 있다면 그 쿼리는 생략된다.
        }
    }
}