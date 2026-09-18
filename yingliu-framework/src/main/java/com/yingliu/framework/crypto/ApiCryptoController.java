package com.yingliu.framework.crypto;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crypto")
public class ApiCryptoController {
    private final ApiCryptoService crypto;

    public ApiCryptoController(ApiCryptoService crypto) {
        this.crypto = crypto;
    }

    @GetMapping("/public-key")
    public ApiResponse<ApiCryptoService.PublicKeyResponse> publicKey(HttpServletRequest request) {
        return ApiResponse.ok(crypto.publicKey(), (String) request.getAttribute(TraceIdFilter.ATTRIBUTE));
    }

    @PostMapping("/test/echo")
    public ApiCryptoService.EncryptedResponse echo(
            @RequestBody ApiCryptoService.EncryptedRequest body, HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String path = request.getServletPath();
        ApiCryptoService.DecryptedRequest decrypted = crypto.decrypt(request.getMethod(), path, authorization, body);
        return crypto.encrypt(request.getMethod(), path, authorization, decrypted, decrypted.body());
    }
}
