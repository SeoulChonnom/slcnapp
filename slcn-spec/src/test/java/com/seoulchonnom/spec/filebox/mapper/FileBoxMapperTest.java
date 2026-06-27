package com.seoulchonnom.spec.filebox.mapper;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.facade.sdo.FileAssetRdo;

class FileBoxMapperTest {
	private static Object enumValue(String className, String name) throws Exception {
		return Enum.valueOf(enumType(className), name);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Class<Enum> enumType(String className) throws Exception {
		return (Class<Enum>)Class.forName(className);
	}

	private static Object newInstance(String className) throws Exception {
		return Class.forName(className).getDeclaredConstructor().newInstance();
	}

	private static Object invoke(Object target, String methodName, Object... args) throws Exception {
		for (Method method : target.getClass().getMethods()) {
			if (method.getName().equals(methodName) && method.getParameterCount() == args.length &&
				isCompatible(method.getParameterTypes(), args)) {
				return method.invoke(target, args);
			}
		}
		fail("No method named %s with %d argument(s) found on %s", methodName, args.length,
			target.getClass().getName());
		return null;
	}

	private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
		for (int i = 0; i < parameterTypes.length; i++) {
			if (args[i] != null && !wrap(parameterTypes[i]).isInstance(args[i])) {
				return false;
			}
		}
		return true;
	}

	private static Object get(Object target, String propertyName) throws Exception {
		String methodName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
		return target.getClass().getMethod(methodName).invoke(target);
	}

	private static void set(Object target, String propertyName, Object value) throws Exception {
		Method setter = findSetter(target.getClass(), propertyName, value);
		setter.invoke(target, value);
	}

	private static Method findSetter(Class<?> type, String propertyName, Object value) {
		String methodName = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
		for (Method method : type.getMethods()) {
			if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
				continue;
			}
			if (value == null || wrap(method.getParameterTypes()[0]).isInstance(value)) {
				return method;
			}
		}
		fail("No setter named %s found on %s", methodName, type.getName());
		return null;
	}

	private static Class<?> wrap(Class<?> type) {
		if (!type.isPrimitive()) {
			return type;
		}
		if (type == int.class) {
			return Integer.class;
		}
		if (type == boolean.class) {
			return Boolean.class;
		}
		if (type == long.class) {
			return Long.class;
		}
		return type;
	}

	@Test
	void toFileBoxItem_shouldMapCreatePayloadWithoutClientGeneratedId() throws Exception {
		Object mapper = newMapper();
		Object cdo = newInstance("com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemCdo");
		set(cdo, "fileAssetId", "file-1");
		set(cdo, "targetType", enumValue("com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType", "TRAVEL_DAY"));
		set(cdo, "targetId", "2026-06-01");
		set(cdo, "role", enumValue("com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole", "GALLERY"));
		set(cdo, "caption", "첫째 날 사진");
		set(cdo, "sortOrder", 3);

		Object item = invoke(mapper, "toFileBoxItem", cdo);

		assertThat(get(item, "id")).isNull();
		assertThat(get(item, "fileAssetId")).isEqualTo("file-1");
		assertThat(get(item, "targetType").toString()).isEqualTo("TRAVEL_DAY");
		assertThat(get(item, "targetId")).isEqualTo("2026-06-01");
		assertThat(get(item, "role").toString()).isEqualTo("GALLERY");
		assertThat(get(item, "caption")).isEqualTo("첫째 날 사진");
		assertThat(get(item, "sortOrder")).isEqualTo(3);
	}

	@Test
	void toFileBoxItem_shouldPreserveServerGeneratedIdFromUpdatePayload() throws Exception {
		Object mapper = newMapper();
		Object udo = newInstance("com.seoulchonnom.spec.filebox.facade.sdo.FileBoxItemUdo");
		set(udo, "id", "file-item-1");
		set(udo, "fileAssetId", "file-1");
		set(udo, "targetType", enumValue("com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType", "TRAVEL_PLACE"));
		set(udo, "targetId", "place-1");
		set(udo, "role", enumValue("com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole", "COVER"));
		set(udo, "caption", "장소 대표 사진");
		set(udo, "sortOrder", 1);

		Object item = invoke(mapper, "toFileBoxItem", udo);

		assertThat(get(item, "id")).isEqualTo("file-item-1");
		assertThat(get(item, "fileAssetId")).isEqualTo("file-1");
		assertThat(get(item, "targetType").toString()).isEqualTo("TRAVEL_PLACE");
		assertThat(get(item, "targetId")).isEqualTo("place-1");
		assertThat(get(item, "role").toString()).isEqualTo("COVER");
		assertThat(get(item, "caption")).isEqualTo("장소 대표 사진");
		assertThat(get(item, "sortOrder")).isEqualTo(1);
	}

	@Test
	void toFileBoxItemRdo_shouldAttachResolvedFileAssetForApiProjection() throws Exception {
		Object mapper = newMapper();
		Object item = newInstance("com.seoulchonnom.spec.filebox.entity.vo.FileBoxItem");
		set(item, "id", "file-item-1");
		set(item, "fileAssetId", "file-1");
		set(item, "targetType", enumValue("com.seoulchonnom.spec.filebox.entity.vo.FileBoxTargetType", "TRAVEL"));
		set(item, "role", enumValue("com.seoulchonnom.spec.filebox.entity.vo.FileBoxItemRole", "COVER"));
		set(item, "caption", "대표 사진");
		set(item, "sortOrder", 1);
		FileAssetRdo file = new FileAssetRdo("file-1", FileType.TRAVEL, "cover.png", "cover-stored.png",
			"travel/cover-stored.png", "image/png", 10L);

		Object rdo = invoke(mapper, "toFileBoxItemRdo", item, file);

		assertThat(get(rdo, "id")).isEqualTo("file-item-1");
		assertThat(get(rdo, "fileAssetId")).isEqualTo("file-1");
		assertThat(get(rdo, "targetType").toString()).isEqualTo("TRAVEL");
		assertThat(get(rdo, "targetId")).isNull();
		assertThat(get(rdo, "role").toString()).isEqualTo("COVER");
		assertThat(get(rdo, "caption")).isEqualTo("대표 사진");
		assertThat(get(rdo, "sortOrder")).isEqualTo(1);
		assertThat(get(rdo, "file")).isSameAs(file);
	}

	private Object newMapper() throws Exception {
		Class<?> mapperType = Class.forName("com.seoulchonnom.spec.filebox.mapper.FileBoxMapper");
		if (mapperType.isInterface()) {
			return org.mapstruct.factory.Mappers.getMapper(mapperType);
		}
		return mapperType.getDeclaredConstructor().newInstance();
	}
}
