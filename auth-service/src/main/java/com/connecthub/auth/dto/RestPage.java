package com.connecthub.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

/**
 * Jackson-deserializable wrapper for Spring's Page — required when a Feign client
 * returns Page<T>, since PageImpl has no default constructor for Jackson binding.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) is critical: Spring serializes Page
 * with a "pageable" field whose type is the abstract Pageable interface. Without this
 * annotation Jackson tries to deserialize it and throws InvalidDefinitionException
 * because Pageable has no concrete constructor. We only need content/number/size/
 * totalElements, so all other Page fields are safely ignored.
 */
@JsonIgnoreProperties(value = { "pageable", "sort" }, ignoreUnknown = true)
public class RestPage<T> extends PageImpl<T> {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RestPage(
            @JsonProperty("content") List<T> content,
            @JsonProperty("number") int number,
            @JsonProperty("size") int size,
            @JsonProperty("totalElements") long totalElements) {
        super(content, PageRequest.of(number, size == 0 ? 1 : size), totalElements);
    }
}
