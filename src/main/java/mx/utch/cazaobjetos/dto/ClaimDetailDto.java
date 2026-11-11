package mx.utch.cazaobjetos.dto;
public record ClaimDetailDto(ClaimResponseDto claim,FoundItemResponseDto foundItem,
        DirectoryUserDto responsible,DirectoryUserDto claimant) {}
