package br.com.specvora_service.config;

import br.com.specvora_service.auth.service.JwtTokenService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null) {
            path = request.getRequestURI();
        }
        return path.equals("/auth/login")
                || path.equals("/auth/register")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();

        // 1. Validação via JWT nativo com claims, audience e roles (ADMINISTRADOR, GESTOR, USER)
        try {
            Authentication auth = jwtTokenService.getAuthentication(token);
            if (auth != null) {
                if (auth instanceof UsernamePasswordAuthenticationToken userAuth) {
                    userAuth.setDetails(Map.of(
                            "expiresAt", jwtTokenService.getExpiration(token).toString(),
                            "remoteAddress", request.getRemoteAddr()
                    ));
                }
                SecurityContextHolder.getContext().setAuthentication(auth);
                filterChain.doFilter(request, response);
                return;
            }
        } catch (JWTVerificationException ignored) {
            // Token não é JWT nativo válido; tentará validar via Firebase caso ativo
        }

        // 2. Validação via Firebase Authentication SDK (se configurado)
        if (!FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(token);
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    String anonymizedUid = anonymizeUid(firebaseToken.getUid());
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    anonymizedUid,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                            );
                    auth.setDetails(Map.of(
                            "expiresAt", "firebase-managed",
                            "remoteAddress", request.getRemoteAddr()
                    ));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    filterChain.doFilter(request, response);
                    return;
                }
            } catch (FirebaseAuthException ignored) {
                // Token inválido no Firebase
            }
        }

        // 3. Rejeição com formato padronizado ErrorResponseDTO
        errorResponseWriter.write(response, request, HttpStatus.UNAUTHORIZED,
                "Token JWT inválido, expirado ou não reconhecido");
    }

    private String anonymizeUid(String uid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(uid.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to anonymize user identifier", ex);
        }
    }
}
