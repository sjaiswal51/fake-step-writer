package com.stepwriter.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : AppCompatActivity() {

    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var tvStatus: TextView
    private lateinit var editStepCount: EditText
    private lateinit var btnWrite: Button
    private lateinit var btnRead: Button

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class)
    )

    private val requestPermissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            tvStatus.text = if (granted.containsAll(permissions)) {
                "Permissions granted. Ready to use Health Connect."
            } else {
                "Health Connect permissions were not granted."
            }
            setControlsEnabled(true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        editStepCount = findViewById(R.id.editStepCount)
        btnWrite = findViewById(R.id.btnWriteSteps)
        btnRead = findViewById(R.id.btnReadSteps)

        val availability = HealthConnectClient.getSdkStatus(this)
        if (availability != HealthConnectClient.SDK_AVAILABLE) {
            tvStatus.text = "Health Connect is not available on this device."
            setControlsEnabled(false)
            return
        }

        healthConnectClient = HealthConnectClient.getOrCreate(this)
        btnWrite.setOnClickListener {
            val count = editStepCount.text.toString().trim().toLongOrNull()
            if (count == null || count !in 1L..MAX_STEPS_PER_WRITE) {
                tvStatus.text = "Enter a step count between 1 and $MAX_STEPS_PER_WRITE."
                return@setOnClickListener
            }
            writeSteps(count)
        }
        btnRead.setOnClickListener { readTodaySteps() }

        lifecycleScope.launch {
            try {
                ensurePermissions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                tvStatus.text = "Unable to check Health Connect permissions."
            }
        }
    }

    private suspend fun ensurePermissions(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        if (granted.containsAll(permissions)) return true

        tvStatus.text = "Grant Health Connect permissions to continue."
        requestPermissionLauncher.launch(permissions)
        return false
    }

    private fun setControlsEnabled(enabled: Boolean) {
        if (::btnWrite.isInitialized) btnWrite.isEnabled = enabled
        if (::btnRead.isInitialized) btnRead.isEnabled = enabled
    }

    private fun writeSteps(count: Long) {
        setControlsEnabled(false)
        tvStatus.text = "Preparing $count test steps…"

        lifecycleScope.launch {
            try {
                if (!ensurePermissions()) return@launch

                val zone = ZoneId.systemDefault()
                val plan = withContext(Dispatchers.Default) {
                    StepSessionPlanner.plan(
                        count = count,
                        date = LocalDate.now(zone),
                        zone = zone,
                        now = java.time.Instant.now()
                    )
                }
                val records = plan.map {
                    StepsRecord(
                        count = it.count,
                        startTime = it.startTime,
                        endTime = it.endTime,
                        startZoneOffset = it.zoneOffset,
                        endZoneOffset = it.zoneOffset
                    )
                }

                // Confirm the intended batch is valid before any write.
                require(records.isNotEmpty()) { "No valid session windows are available yet today." }
                require(records.sumOf { it.count } == count) { "The planned total is invalid." }
                require(records.all { it.startTime < it.endTime }) { "A session has an invalid time interval." }

                healthConnectClient.insertRecords(records)
                tvStatus.text =
                    "Wrote $count synthetic/test steps to Health Connect. " +
                    "Other apps may apply their own source and data validation."
                Toast.makeText(this@MainActivity, "Test records written.", Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                tvStatus.text = "No valid session windows are available for the current date and time."
            } catch (_: SecurityException) {
                tvStatus.text = "Health Connect permission is missing. Check permissions in Settings."
            } catch (_: Exception) {
                tvStatus.text = "Unable to write step records. Check Health Connect availability and permissions."
            } finally {
                setControlsEnabled(true)
            }
        }
    }

    private fun readTodaySteps() {
        setControlsEnabled(false)
        tvStatus.text = "Reading today's Health Connect total…"

        lifecycleScope.launch {
            try {
                if (!ensurePermissions()) return@launch

                val result = withContext(Dispatchers.IO) {
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    val startOfDay = today.atStartOfDay(zone).toInstant()
                    val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant()

                    healthConnectClient.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                }

                val steps = result[StepsRecord.COUNT_TOTAL] ?: 0L
                tvStatus.text = "Today's Health Connect total: $steps steps. This may include records from other apps."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                tvStatus.text = "Health Connect read permission is missing."
            } catch (_: Exception) {
                tvStatus.text = "Unable to read today's total from Health Connect."
            } finally {
                setControlsEnabled(true)
            }
        }
    }

    private companion object {
        const val MAX_STEPS_PER_WRITE = 100_000L
    }
}
