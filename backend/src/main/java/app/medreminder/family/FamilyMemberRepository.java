package app.medreminder.family;

import app.medreminder.family.FamilyDtos.MemberResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {
    Optional<FamilyMember> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Optional<FamilyMember> findByIdAndFamilyId(UUID id, UUID familyId);

    long countByFamilyIdAndRole(UUID familyId, Role role);

    @Query("""
            select new app.medreminder.family.FamilyDtos$MemberResponse(
                m.id, m.userId, u.displayName, u.email, m.role,
                case when m.userId = :me then true else false end)
            from FamilyMember m, app.medreminder.user.AppUser u
            where u.id = m.userId and m.familyId = :familyId
            order by m.createdAt""")
    List<MemberResponse> listMembers(@Param("familyId") UUID familyId, @Param("me") UUID me);
}
