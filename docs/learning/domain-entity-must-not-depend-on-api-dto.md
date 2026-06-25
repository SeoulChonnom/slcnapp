# Domain Entity Must Not Depend On API DTO

## Context
During the file reference API change, `Trip` temporarily depended on a facade file DTO.
That made a domain entity depend on a facade/API DTO.
The current file policy uses `FileAsset.id` as the domain reference and returns `FileAssetRdo` only at API boundaries.

## Rule
Do not use `*Cdo`, `*Udo`, `*Rdo`, or `*Sdo` classes inside domain entities or value objects.
DTOs belong at API/application boundaries. Domain entities and value objects should use domain types.

## Preferred Shape
When an API DTO and a domain concept carry similar data, create an explicit domain value object and map at the boundary.

Example:
- API DTO: `FileAssetRdo`
- Domain field: `logoFileId`, `firstMapFileId`, `photoFileId`
- Domain enum: `FileType`
- Persistence mapper: stores the file id in explicit columns, such as `logo_file_id`

## Mapping Responsibility
- `Resource` receives request parameters and DTOs.
- `Logic` may validate request DTOs before creating domain objects.
- Domain entities should receive or own domain value objects.
- `Mapper` classes convert between DTO, domain, and JPA/Mongo persistence shapes.

## Review Checklist
Before finishing changes that add or modify DTOs:
- Check that no entity imports `com.seoulchonnom.spec.*.facade.sdo`.
- Check that no value object imports facade DTOs.
- If persistence still stores a primitive string, keep conversion in a mapper instead of leaking the string shape into API contracts.
- Add focused mapper tests for DTO-domain-persistence conversion.
