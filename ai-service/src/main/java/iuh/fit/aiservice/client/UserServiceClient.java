package iuh.fit.aiservice.client;

import iuh.fit.aiservice.config.AiFeignConfig;
import iuh.fit.aiservice.dto.client.CustomerProfileResponse;
import iuh.fit.shared.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", configuration = AiFeignConfig.class)
public interface UserServiceClient {

    @GetMapping("/api/v1/user/customers/account/{accountId}")
    ApiResponse<CustomerProfileResponse> getCustomerById(@PathVariable("accountId") String accountId);
}
