package uz.sonic.hr.employee;

import uz.sonic.hr.common.enums.*;
import uz.sonic.hr.employee.*;
import uz.sonic.hr.admin.*;
import uz.sonic.hr.team.*;
import uz.sonic.hr.task.*;
import uz.sonic.hr.ticket.*;
import uz.sonic.hr.notification.*;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import uz.sonic.hr.common.entity.UserDetailsImpl;

import java.util.Collection;
import java.util.List;

/**
 * A person who belongs to one or more teams. Per-team role is held on
 * {@code TeamMembership}; the account's global authority is simply ROLE_USER.
 */
@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Employee extends UserDetailsImpl {

    private String phone;

    @Column(unique = true)
    private Long telegramChatId;

    @Column(unique = true)
    private String telegramLinkCode;

    /** Team currently selected in the Telegram bot (user may belong to several teams). */
    private Long botTeamId;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
