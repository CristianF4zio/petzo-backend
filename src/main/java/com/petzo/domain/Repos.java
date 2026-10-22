package com.petzo.domain;

import com.petzo.domain.Simple.Favorite;
import com.petzo.domain.Simple.FavoriteId;
import com.petzo.domain.Simple.Municipality;
import com.petzo.domain.Simple.Report;
import com.petzo.domain.Types.PetStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** All Spring Data repositories (nested; enabled with considerNestedRepositories). */
public final class Repos {

	private Repos() {}

	public interface MunicipalityRepo extends JpaRepository<Municipality, Short> {}

	public interface ProfileRepo extends JpaRepository<Profile, UUID> {
		List<Profile> findByVerificationStatusOrderByUpdatedAtDesc(Types.VerificationStatus status);
	}

	public interface PetRepo extends JpaRepository<Pet, UUID>, JpaSpecificationExecutor<Pet> {
		List<Pet> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

		List<Pet> findByStatusOrderByCreatedAt(PetStatus status);

		long countByOwnerIdAndStatusIn(UUID ownerId, Collection<PetStatus> statuses);

		long countByOwnerIdAndStatus(UUID ownerId, PetStatus status);

		long countByStatus(PetStatus status);

		@Query("select p from Pet p where p.id in :ids and p.status = 'approved' and p.owner.suspended = false")
		List<Pet> findVisible(Collection<UUID> ids);
	}

	public interface FavoriteRepo extends JpaRepository<Favorite, FavoriteId> {
		List<Favorite> findByUserIdOrderByCreatedAtDesc(UUID userId);
	}

	public interface ReportRepo extends JpaRepository<Report, UUID> {
		List<Report> findByStatusOrderByCreatedAt(Types.ReportStatus status);
	}
}
