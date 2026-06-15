package com.exemplo.secrest.service;

import com.exemplo.secrest.dto.CreateUserDto;
import com.exemplo.secrest.dto.EmailDto;
import com.exemplo.secrest.dto.LoginUserDto;
import com.exemplo.secrest.dto.RecoveryJwtTokenDto;
import com.exemplo.secrest.dto.UpdateProfileDto;
import com.exemplo.secrest.dto.UserProfileDto;
import com.exemplo.secrest.entity.Role;
import com.exemplo.secrest.entity.User;
import com.exemplo.secrest.enums.RoleName;
import com.exemplo.secrest.producer.UserProducer;
import com.exemplo.secrest.repository.UserRepository;
import com.exemplo.secrest.security.service.JwtTokenService;
import com.exemplo.secrest.security.service.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CodigoCacheService codigoCacheService;

    @Autowired
    private UserProducer userProducer;

    public RecoveryJwtTokenDto authenticateUser(LoginUserDto loginDto) {
        var authToken = new UsernamePasswordAuthenticationToken(
                loginDto.email(), loginDto.password());
        Authentication authentication = authenticationManager.authenticate(authToken);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String token = jwtTokenService.generateToken(userDetails);
        return new RecoveryJwtTokenDto(token);
    }

    public void createUser(CreateUserDto createDto) {
        User newUser = User.builder()
                .email(createDto.email())
                .password(passwordEncoder.encode(createDto.password()))
                .roles(List.of(Role.builder().name(createDto.role()).build()))
                .build();
        userRepository.save(newUser);
    }

    public UserProfileDto getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + email));
        return toProfileDto(user);
    }

    public UserProfileDto updateProfile(String email, UpdateProfileDto dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + email));

        user.setName(dto.name());
        // apenas uma role por usuário: substitui a lista pela nova role
        user.setRoles(List.of(Role.builder().name(dto.role()).build()));

        User updated = userRepository.save(user);
        return toProfileDto(updated);
    }

    private UserProfileDto toProfileDto(User user) {
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .toList();
        return new UserProfileDto(user.getId(), user.getName(), user.getEmail(), roles);
    }

    public void requestCode(String email) {
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User temp = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .roles(List.of(Role.builder().name(RoleName.ROLE_CUSTOMER).build()))
                    .build();
            return userRepository.save(temp);
        });

        String code = String.format("%06d", new Random().nextInt(1_000_000));
        codigoCacheService.armazenar(email, code);

        EmailDto emailDto = new EmailDto(
                email,
                "Seu código de acesso",
                "Seu código de acesso é: " + code,
                UUID.randomUUID()
        );
        userProducer.publicarMensagemEmail(emailDto);
    }

    public RecoveryJwtTokenDto verifyCode(String email, String code) {
        String storedCode = codigoCacheService.buscar(email);
        if (storedCode == null || !storedCode.equals(code)) {
            throw new RuntimeException("Código inválido ou expirado");
        }
        codigoCacheService.remover(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + email));

        String token = jwtTokenService.generateToken(new UserDetailsImpl(user));
        return new RecoveryJwtTokenDto(token);
    }
}
