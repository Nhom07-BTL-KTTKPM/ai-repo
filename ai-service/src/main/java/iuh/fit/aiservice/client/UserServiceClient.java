package iuh.fit.aiservice.client;

import iuh.fit.aiservice.config.AiFeignConfig;
import iuh.fit.aiservice.dto.client.CustomerProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", configuration = AiFeignConfig.class)
public interface UserServiceClient {

    @GetMapping("/api/v1/user/customers/{customerId}")
    CustomerProfileResponse getCustomerById(@PathVariable("customerId") String customerId);
}
