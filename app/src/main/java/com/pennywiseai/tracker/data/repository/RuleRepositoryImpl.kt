package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.RuleApplicationDao
import com.pennywiseai.tracker.data.database.dao.RuleDao
import com.pennywiseai.tracker.data.database.entity.RuleApplicationEntity
import com.pennywiseai.tracker.data.database.entity.RuleEntity
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepositoryImpl @Inject constructor(
    private val ruleDao: RuleDao,
    private val ruleApplicationDao: RuleApplicationDao,
    private val ruleEngine: RuleEngine
) : RuleRepository {

    override fun getAllRules(): Flow<List<TransactionRule>> {
        return ruleDao.getAllRules().map { entities ->
            entities.map { entity -> entityToRule(entity) }
        }
    }

    override suspend fun getActiveRules(): List<TransactionRule> {
        return ruleDao.getActiveRules().map { entity ->
            entityToRule(entity)
        }
    }

    override suspend fun getRuleById(ruleId: String): TransactionRule? {
        return ruleDao.getRuleById(ruleId)?.let { entityToRule(it) }
    }

    override suspend fun insertRule(rule: TransactionRule) {
        ruleDao.insertRule(ruleToEntity(rule))
    }

    override suspend fun updateRule(rule: TransactionRule) {
        ruleDao.updateRule(ruleToEntity(rule))
    }

    override suspend fun deleteRule(ruleId: String) {
        ruleDao.deleteRuleById(ruleId)
    }

    override suspend fun setRuleActive(ruleId: String, isActive: Boolean) {
        ruleDao.setRuleActive(ruleId, isActive)
    }

    override suspend fun updateRulePriority(ruleId: String, priority: Int) {
        ruleDao.updateRulePriority(ruleId, priority)
    }

    override suspend fun saveRuleApplication(application: RuleApplication) {
        ruleApplicationDao.insertApplication(applicationToEntity(application))
    }

    override suspend fun saveRuleApplications(applications: List<RuleApplication>) {
        ruleApplicationDao.insertApplications(
            applications.map { applicationToEntity(it) }
        )
    }

    override suspend fun getRuleApplicationsForTransaction(transactionId: String): List<RuleApplication> {
        return ruleApplicationDao.getApplicationsByTransaction(transactionId)
            .map { entityToApplication(it) }
    }

    override fun getRuleApplicationsForRule(ruleId: String): Flow<List<RuleApplication>> {
        return ruleApplicationDao.getApplicationsByRule(ruleId).map { entities ->
            entities.map { entityToApplication(it) }
        }
    }

    override suspend fun getRuleApplicationCount(ruleId: String): Int {
        return ruleApplicationDao.getApplicationCountForRule(ruleId)
    }

    private fun ruleToEntity(rule: TransactionRule): RuleEntity {
        return RuleEntity(
            id = rule.id,
            name = rule.name,
            description = rule.description,
            priority = rule.priority,
            conditions = ruleEngine.serializeConditions(rule.conditions),
            actions = ruleEngine.serializeActions(rule.actions),
            isActive = rule.isActive,
            isSystemTemplate = rule.isSystemTemplate,
            createdAt = LocalDateTime.ofEpochSecond(rule.createdAt / 1000, 0, ZoneOffset.UTC),
            updatedAt = LocalDateTime.ofEpochSecond(rule.updatedAt / 1000, 0, ZoneOffset.UTC)
        )
    }

    private fun entityToRule(entity: RuleEntity): TransactionRule {
        return TransactionRule(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            priority = entity.priority,
            conditions = ruleEngine.deserializeConditions(entity.conditions),
            actions = ruleEngine.deserializeActions(entity.actions),
            isActive = entity.isActive,
            isSystemTemplate = entity.isSystemTemplate,
            createdAt = entity.createdAt.toEpochSecond(ZoneOffset.UTC) * 1000,
            updatedAt = entity.updatedAt.toEpochSecond(ZoneOffset.UTC) * 1000
        )
    }

    private fun applicationToEntity(application: RuleApplication): RuleApplicationEntity {
        return RuleApplicationEntity(
            id = application.id,
            ruleId = application.ruleId,
            ruleName = application.ruleName,
            transactionId = application.transactionId,
            fieldsModified = ruleEngine.serializeFieldModifications(application.fieldsModified),
            appliedAt = LocalDateTime.ofEpochSecond(application.appliedAt / 1000, 0, ZoneOffset.UTC)
        )
    }

    private fun entityToApplication(entity: RuleApplicationEntity): RuleApplication {
        return RuleApplication(
            id = entity.id,
            ruleId = entity.ruleId,
            ruleName = entity.ruleName,
            transactionId = entity.transactionId,
            fieldsModified = ruleEngine.deserializeFieldModifications(entity.fieldsModified),
            appliedAt = entity.appliedAt.toEpochSecond(ZoneOffset.UTC) * 1000
        )
    }
}