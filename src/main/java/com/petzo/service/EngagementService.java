package com.petzo.service;

import com.petzo.common.ApiException;
import com.petzo.domain.Profile;
import com.petzo.domain.Repos.FavoriteRepo;
import com.petzo.domain.Repos.PetRepo;
import com.petzo.domain.Repos.ReportRepo;
import com.petzo.domain.Simple.Favorite;
import com.petzo.domain.Simple.FavoriteId;
import com.petzo.domain.Simple.Report;
import com.petzo.web.Dtos.PetDto;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Favorites (HU-16) and reports (HU-11). */
@Service
@Transactional
public class EngagementService {

	private final FavoriteRepo favorites;
	private final ReportRepo reports;
	private final PetRepo pets;
	private final PetService petService;

	public EngagementService(FavoriteRepo favorites, ReportRepo reports, PetRepo pets, PetService petService) {
		this.favorites = favorites;
		this.reports = reports;
		this.pets = pets;
		this.petService = petService;
	}

	private void requireVisible(UUID petId) {
		if (pets.findVisible(List.of(petId)).isEmpty()) throw ApiException.notFound();
	}

	public void addFavorite(Profile me, UUID petId) {
		requireVisible(petId);
		Favorite f = new Favorite();
		f.userId = me.id;
		f.petId = petId;
		if (!favorites.existsById(new FavoriteId(me.id, petId))) favorites.save(f);
	}

	public void removeFavorite(Profile me, UUID petId) {
		favorites.deleteById(new FavoriteId(me.id, petId));
	}

	// Adopted or hidden pets drop out silently
	@Transactional(readOnly = true)
	public List<PetDto> favorites(Profile me) {
		var ids = favorites.findByUserIdOrderByCreatedAtDesc(me.id).stream().map(f -> f.petId).toList();
		return ids.isEmpty() ? List.of() : pets.findVisible(ids).stream().map(p -> petService.toDto(p, false, null)).toList();
	}

	public void report(Profile me, UUID petId, String reason) {
		requireVisible(petId);
		Report r = new Report();
		r.petId = petId;
		r.reporterId = me.id;
		r.reason = reason.trim();
		reports.save(r);
	}
}
