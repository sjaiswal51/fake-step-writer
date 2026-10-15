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
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect test utility.
 *
 * Records created by this app are explicitly marked as manually entered test data.
 * This app does not try to represent the records as sensor-captured activity.
 */
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
                "Health Connect permissions granted."
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

        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            tvStatus.text = "Health Connect is not available on this device."
            setControlsEnabled(false)
            return
        }

        healthConnectClient = HealthConnectClient.getOrCreate(this)
        btnWrite.setOnClickListener {
            val count = editStepCount.text.toString().trim().toLongOrNull()
            if (!StepInputValidator.isValidCount(count)) {
                tvStatus.text = "Enter a count between 1 and ${StepInputValidator.MAX_STEPS_PER_WRITE}."
                return@setOnClickListener
            }
            writeTestRecord(count!!)
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

    private fun writeTestRecord(count: Long) {
        setControlsEnabled(false)
        tvStatus.text = "Writing a clearly marked synthetic test record…"

        lifecycleScope.launch {
            try {
                if (!ensurePermissions()) return@launch

                val zone = ZoneId.systemDefault()
                val now = Instant.now()
                val start = now.minusSeconds(TEST_RECORD_DURATION_SECONDS)
                val offset = zone.rules.getOffset(now)

                require(StepInputValidator.isValidInterval(start, now)) {
                    "Test record has an invalid time interval."
                }

                val record = StepsRecord(
                    count = count,
                    startTime = start,
                    endTime = now,
                    startZoneOffset = offset,
                    endZoneOffset = offset,
                    metadata = Metadata.manualEntry()
                )

                healthConnectClient.insertRecords(listOf(record))
                tvStatus.text =
                    "Synthetic test record submitted as a manual entry. " +
                    "Other apps may ignore manual records or apply their own validation."
                Toast.makeText(this@MainActivity, "Test record submitted.", Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                tvStatus.text = "The test record is invalid. Check the step count and try again."
            } catch (_: SecurityException) {
                tvStatus.text = "Health Connect permission is missing. Check permissions in Settings."
            } catch (_: Exception) {
                tvStatus.text = "Unable to write the test record. Check Health Connect availability and permissions."
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
                tvStatus.text = "Today's Health Connect total: $steps steps. This can include records from other apps."
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
        const val TEST_RECORD_DURATION_SECONDS = 60L
    }
}
