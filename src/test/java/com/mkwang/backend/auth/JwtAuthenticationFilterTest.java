package com.mkwang.backend.auth;

import com.mkwang.backend.modules.auth.security.*;
import com.mkwang.backend.modules.user.entity.User;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private JwtService jwt;
    private JwtAuthenticationEntryPoint entry;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    @BeforeEach void prepare() {
        jwt=mock(JwtService.class);entry=mock(JwtAuthenticationEntryPoint.class);
        var users=mock(UserDetailsService.class);var adapter=mock(UserDetailsAdapter.class);var user=mock(User.class);
        when(jwt.extractUsername("test-token")).thenReturn("test@integration.invalid");
        when(users.loadUserByUsername("test@integration.invalid")).thenReturn(adapter);
        when(adapter.isEnabled()).thenReturn(true);when(adapter.isAccountNonLocked()).thenReturn(true);
        when(adapter.getUser()).thenReturn(user);when(user.getId()).thenReturn(1L);
        when(adapter.getAuthorities()).thenReturn(List.of());
        when(jwt.isTokenValid("test-token",adapter)).thenReturn(true);when(jwt.isTokenVersionValid("test-token",1L)).thenReturn(true);
        filter=new JwtAuthenticationFilter(jwt,users,entry);
        request=new MockHttpServletRequest("GET","/accountant/ledger");request.setServletPath("/accountant/ledger");
        request.addHeader("Authorization","Bearer test-token");response=new MockHttpServletResponse();
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void downstreamFailureIsNotRewrittenAsAuthenticationFailure() throws Exception {
        FilterChain chain=(request,response)->{throw new IOException("Ledger downstream failure");};
        assertThrows(IOException.class,()->filter.doFilter(request,response,chain));verifyNoInteractions(entry);
    }
    @Test void committedStreamFailureDoesNotAttemptSecondResponse() throws Exception {
        FilterChain chain=(request,response)->{response.flushBuffer();throw new IOException("Stream closed");};
        assertThrows(IOException.class,()->filter.doFilter(request,response,chain));verifyNoInteractions(entry);
    }
    @Test void invalidTokenStillStopsTheRequest() throws Exception {
        when(jwt.extractUsername("test-token")).thenThrow(new MalformedJwtException("Invalid fixture token"));
        var chain=mock(FilterChain.class);filter.doFilter(request,response,chain);
        verify(entry).commence(eq(request),eq(response),any());verifyNoInteractions(chain);
    }
}
