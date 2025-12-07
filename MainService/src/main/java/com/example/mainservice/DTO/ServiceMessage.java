package com.example.mainservice.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceMessage {
    private String serviceName;
    private String content;
    private double weight;
}
