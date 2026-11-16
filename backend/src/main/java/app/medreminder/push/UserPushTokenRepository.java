package app.medreminder.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, UUID> {
    Optional<UserPushToken> findByToken(String token);

    @Modifying
    @Query("delete from UserPushToken t where t.token = :token")
    void deleteByToken(@Param("token") String token);

    /** Push tokens of everyone in a family, with each member's app language for the alert text. */
    @Query("""
            select new app.medreminder.push.UserPushTokenRepository$Target(t.token, u.appLanguage)
            from UserPushToken t, app.medreminder.family.FamilyMember m, app.medreminder.user.AppUser u
            where m.userId = t.userId and u.id = t.userId and m.familyId = :familyId""")
    List<Target> targetsInFamily(@Param("familyId") UUID familyId);

    record Target(String token, String language) {
    }
}
