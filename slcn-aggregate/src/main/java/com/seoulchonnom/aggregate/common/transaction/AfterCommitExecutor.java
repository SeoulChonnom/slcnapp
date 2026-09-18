package com.seoulchonnom.aggregate.common.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * PostgreSQL 커밋이 끝난 뒤에 실행할 작업을 등록한다.
 *
 * MongoDB는 JPA 트랜잭션에 참여하지 않으므로 @Transactional 메서드 본문에서 Mongo를 건드리면
 * 그 변경은 RDB 커밋보다 **항상 먼저** 확정된다. 메서드 안에서 문장 순서를 바꿔도 소용없다 —
 * RDB가 durable해지는 시점은 메서드가 반환된 뒤 트랜잭션 프록시가 commit을 호출하는 순간뿐이다.
 *
 * 삭제에서 이 순서가 뒤집히면 RDB 커밋 실패 시 사진 목록의 유일한 원본인 FileBox 문서가
 * 이미 사라진 상태로 남아 복구가 불가능해진다. 그래서 삭제는 afterCommit으로 미룬다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AfterCommitExecutor {
	/**
	 * 트랜잭션이 없으면 즉시 실행한다. 호출자가 트랜잭션 밖에서 쓰더라도 동작이 달라지지 않게 한다.
	 */
	public static void run(Runnable task) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			task.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				task.run();
			}
		});
	}
}
