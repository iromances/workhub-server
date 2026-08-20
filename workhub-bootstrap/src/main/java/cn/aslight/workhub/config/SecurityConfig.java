package cn.aslight.workhub.config;

import cn.aslight.workhub.security.JwtAuthenticationFilter;
import cn.aslight.workhub.security.McpAccessTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
/**
 * SecurityConfig 模型。
 */
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RestAuthenticationHandlers restAuthenticationHandlers,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   McpAccessTokenAuthenticationFilter mcpAccessTokenAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restAuthenticationHandlers)
                        .accessDeniedHandler(restAuthenticationHandlers)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/system/ping", "/api/auth/login", "/api/auth/avatars/**", "/api/wecom/callback/**").permitAll()
                        .requestMatchers("/api/mcp/runtime").hasRole("MCP")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(mcpAccessTokenAuthenticationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
