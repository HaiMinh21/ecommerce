package com.adidas.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequestDTO {

    private String firstName;

    private String lastName;

    private String phone;
}
