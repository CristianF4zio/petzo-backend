package com.petzo.service;

import com.petzo.common.ApiException;
import com.petzo.domain.Profile;
import com.petzo.domain.Repos.MunicipalityRepo;
import com.petzo.domain.Repos.ProfileRepo;
import com.petzo.domain.Types.AccountType;
import com.petzo.domain.Types.VerificationStatus;
import com.petzo.storage.StorageClient;
import com.petzo.web.Dtos.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileService {

	private final ProfileRepo profiles;
	private final MunicipalityRepo municipalities;
	private final StorageClient storage;

	public ProfileService(ProfileRepo profiles, MunicipalityRepo municipalities, StorageClient storage) {
		this.profiles = profiles;
		this.municipalities = municipalities;
		this.storage = storage;
	}

	/** Venezuelan mobile: 0412 123 4567 / +58 412-1234567 / +58 0412… → +584121234567, else null. */
	public static String normalizeWhatsapp(String raw) {
		String d = raw.replaceAll("[\\s\\-()]", "").replaceFirst("^(\\+?58)?0?", "");
		return d.matches("4\\d{9}") ? "+58" + d : null;
	}

	@Transactional(readOnly = true)
	public List<MunicipalityDto> municipalities() {
		return municipalities.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(m -> new MunicipalityDto(m.id, m.name)).toList();
	}

	public ProfileDto update(Profile me, UpdateMe in) {
		String wa = normalizeWhatsapp(in.whatsapp());
		if (wa == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Número venezolano inválido");
		me.fullName = in.fullName().trim();
		me.whatsapp = wa;
		me.municipalityId = in.municipalityId();
		return toDto(profiles.saveAndFlush(me), storage);
	}

	// Same path on every upload (SPEC §15); the version param busts caches
	public ProfileDto uploadAvatar(Profile me, org.springframework.web.multipart.MultipartFile file) {
		storage.upload(avatarPath(me), StorageClient.readJpeg(file), "image/jpeg");
		me.avatarUrl = storage.publicUrl(avatarPath(me)) + "?v=" + System.currentTimeMillis();
		return toDto(profiles.saveAndFlush(me), storage);
	}

	public ProfileDto me(Profile me) { return toDto(me, storage); }

	public ProfileDto requestFoundation(Profile me, FoundationRequest in) {
		if (me.verificationStatus == VerificationStatus.verified) throw ApiException.conflict("ALREADY_VERIFIED", "Tu fundación ya está verificada");
		me.accountType = AccountType.foundation;
		me.foundationName = in.foundationName().trim();
		me.foundationInstagram = in.foundationInstagram().replaceFirst("^@", "");
		me.foundationBio = in.foundationBio();
		me.verificationStatus = VerificationStatus.pending;
		return toDto(profiles.saveAndFlush(me), storage);
	}

	private static String avatarPath(Profile me) { return "avatars/" + me.id + ".jpg"; }

	public static ProfileDto toDto(Profile p, StorageClient storage) {
		return new ProfileDto(p.id, p.fullName, storage.clientAvatar(p.avatarUrl), p.whatsapp, p.municipalityId, p.role, p.accountType, p.foundationName,
				p.foundationInstagram, p.foundationBio, p.verificationStatus, p.plan, p.isComplete(), p.suspended);
	}
}
