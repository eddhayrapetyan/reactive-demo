package com.example.reactivedemo.controller;

import com.example.reactivedemo.entity.Item;
import com.example.reactivedemo.repository.ItemRepository;
import org.reactivestreams.Subscription;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalTime;

@RestController
@RequestMapping("/items")
public class ReactiveController {

    private final ItemRepository itemRepository;
    private final WebClient webClient;

    @Autowired
    public ReactiveController(ItemRepository itemRepository, WebClient.Builder webClientBuilder) {
        this.itemRepository = itemRepository;
        this.webClient = webClientBuilder.baseUrl("https://jsonplaceholder.typicode.com").build();
    }

    @GetMapping
    public Flux<Item> getAllItems() {
        return itemRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Item> createItem(@RequestBody Item item) {
        return itemRepository.save(item);
    }

    @GetMapping("/numbers")
    public Flux<Long> getNumbers() {
        return Flux.interval(Duration.ofSeconds(1))
                .take(10)
                .log();
    }

    @GetMapping("/external")
    public Mono<String> getExternalData() {
        return this.webClient.get()
                .uri("/posts/1")
                .retrieve()
                .bodyToMono(String.class)
                .log();
    }

    @GetMapping(value = "/stream-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamEvents() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(sequence -> "SSE - " + LocalTime.now())
                .log();
    }

    @GetMapping("/error-handling")
    public Flux<String> handleError() {
        return Flux.just("1", "2", "a", "3")
                .map(value -> {
                    try {
                        Integer.parseInt(value);
                        return value;
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Invalid value: " + value);
                    }
                })
                .onErrorResume(e -> {
                    System.err.println(e.getMessage());
                    return Flux.just("Default1", "Default2");
                });
    }

    @GetMapping("/combine")
    public Flux<String> combineSources() {
        Flux<String> source1 = Flux.interval(Duration.ofSeconds(1)).map(i -> "Source1 - " + i);
        Flux<String> source2 = Flux.interval(Duration.ofSeconds(1)).map(i -> "Source2 - " + i);

        return Flux.merge(source1, source2).take(10).log();
    }

    @GetMapping(value = "/backpressure", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> handleBackpressure() {
        return Flux.interval(Duration.ofMillis(10))
                .map(value -> "Value: " + value)
                .onBackpressureBuffer(100, value -> System.out.println("Buffer overflow: " + value))
                .doOnNext(value -> {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
    }

    @GetMapping("/subscribe")
    public Mono<String> subscribeExample() {
        Flux<Long> numberGenerator = Flux.interval(Duration.ofMillis(10))
                .take(200)
                .log();

        numberGenerator.subscribe(new BaseSubscriber<Long>() {
            private int count = 0;

            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                System.out.println("Subscribed");
                request(10);
            }

            @Override
            protected void hookOnNext(Long value) {
                System.out.println("Received: " + value);
                count++;

                // Simulate slow processing
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (count % 10 == 0) {
                    request(10); // Request the next 10 items
                }
            }

            @Override
            protected void hookOnComplete() {
                System.out.println("Completed");
            }

            @Override
            protected void hookOnError(Throwable throwable) {
                System.err.println("Error: " + throwable.getMessage());
            }
        });

        return Mono.just("Subscription example executed. Check logs for details.");
    }

}