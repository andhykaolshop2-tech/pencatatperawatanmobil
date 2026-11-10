package com.personalizeprolabs.odolog.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * Distance driven and energy used between two "full" fill-ups (or charges).
 * This is the classic full-tank method: everything that was put into the tank after
 * the previous full fill-up, up to and including this one, was burnt on [distanceKm].
 */
data class EconomyInterval(
    /** Id of the entry that closes the interval (the later full fill-up). */
    val endEntryId: Long,
    val endEpochDay: Long,
    val distanceKm: Double,
    /** Litres or kWh. */
    val quantity: Double,
    val cost: Double,
)

class EnergyStats(val intervals: List<EconomyInterval>) {
    val distanceKm: Double = intervals.sumOf { it.distanceKm }
    val quantity: Double = intervals.sumOf { it.quantity }
    val cost: Double = intervals.sumOf { it.cost }
    val hasData: Boolean get() = distanceKm > 0.0 && quantity > 0.0

    /** Energy cost per km over the measured intervals, or null without data. */
    val costPerKm: Double? get() = if (distanceKm > 0.0) cost / distanceKm else null
}

data class PeriodSummary(
    val entryCount: Int,
    val fuelCost: Double,
    val chargeCost: Double,
    val otherCost: Double,
    val fuelLiters: Double,
    val chargeKwh: Double,
    /** Highest minus lowest odometer reading in the period; null with fewer than two readings. */
    val distanceKm: Double?,
    val fuelEconomy: EnergyStats,
    val chargeEconomy: EnergyStats,
) {
    val totalCost: Double get() = fuelCost + chargeCost + otherCost
    val energyCost: Double get() = fuelCost + chargeCost

    /** All costs of the period divided by the distance driven in the period. */
    val costPerKm: Double?
        get() {
            val d = distanceKm ?: return null
            return if (d > 0.0) totalCost / d else null
        }
}

data class MonthCost(
    val year: Int,
    val month: Int,
    val energyCost: Double,
    val otherCost: Double,
) {
    val total: Double get() = energyCost + otherCost
}

object Stats {

    /**
     * Oldest first. Entries of the same day are ordered by odometer (entries without a
     * reading come last), then by id. This is a total order, which sorting requires.
     */
    val CHRONOLOGICAL: Comparator<LogEntry> = Comparator { a, b ->
        var c = a.dateEpochDay.compareTo(b.dateEpochDay)
        if (c == 0) c = (a.odometerKm ?: Double.MAX_VALUE).compareTo(b.odometerKm ?: Double.MAX_VALUE)
        if (c == 0) c = a.id.compareTo(b.id)
        c
    }

    /** Newest first, for lists. */
    val NEWEST_FIRST: Comparator<LogEntry> = Comparator { a, b -> CHRONOLOGICAL.compare(b, a) }

    /**
     * Full-to-full intervals for all fill-ups (type FUEL) or all charges (type CHARGE).
     *
     * Rules:
     *  - Only a "full" entry with an odometer reading can start or close an interval.
     *  - Partial fill-ups in between are added to the interval (their odometer is not needed).
     *  - "missedPrevious" breaks the chain: the interval that would end at or after that
     *    entry is dropped and measuring starts again at the next full entry.
     *  - A full entry without odometer, or with an odometer that is not higher than the
     *    previous full one (typing mistake), also breaks the chain.
     */
    fun economyIntervals(entries: List<LogEntry>, type: EntryType): List<EconomyInterval> {
        val fills = entries.filter { it.type == type && it.quantity > 0.0 }.sortedWith(CHRONOLOGICAL)
        val result = ArrayList<EconomyInterval>()
        var anchorKm: Double? = null
        var quantity = 0.0
        var cost = 0.0
        for (e in fills) {
            if (e.missedPrevious) anchorKm = null
            if (anchorKm != null) {
                quantity += e.quantity
                cost += e.totalCost
            }
            if (e.isFull) {
                val odo = e.odometerKm
                val anchor = anchorKm
                if (odo != null && anchor != null && odo > anchor) {
                    result.add(EconomyInterval(e.id, e.dateEpochDay, odo - anchor, quantity, cost))
                }
                anchorKm = odo
                quantity = 0.0
                cost = 0.0
            }
        }
        return result
    }

    /**
     * Totals for the entries dated from [fromEpochDay] to [toEpochDay] (both inclusive,
     * null = open end). Consumption uses the intervals that END inside the period.
     */
    fun summarize(entries: List<LogEntry>, fromEpochDay: Long?, toEpochDay: Long?): PeriodSummary {
        fun inPeriod(day: Long): Boolean =
            (fromEpochDay == null || day >= fromEpochDay) && (toEpochDay == null || day <= toEpochDay)

        var count = 0
        var fuelCost = 0.0
        var chargeCost = 0.0
        var otherCost = 0.0
        var liters = 0.0
        var kwh = 0.0
        var minOdo = Double.MAX_VALUE
        var maxOdo = -Double.MAX_VALUE
        var odoReadings = 0
        for (e in entries) {
            if (!inPeriod(e.dateEpochDay)) continue
            count++
            when (e.type) {
                EntryType.FUEL -> {
                    fuelCost += e.totalCost
                    liters += e.quantity
                }
                EntryType.CHARGE -> {
                    chargeCost += e.totalCost
                    kwh += e.quantity
                }
                EntryType.EXPENSE -> otherCost += e.totalCost
            }
            val odo = e.odometerKm
            if (odo != null) {
                odoReadings++
                if (odo < minOdo) minOdo = odo
                if (odo > maxOdo) maxOdo = odo
            }
        }
        val distance = if (odoReadings >= 2 && maxOdo > minOdo) maxOdo - minOdo else null
        return PeriodSummary(
            entryCount = count,
            fuelCost = fuelCost,
            chargeCost = chargeCost,
            otherCost = otherCost,
            fuelLiters = liters,
            chargeKwh = kwh,
            distanceKm = distance,
            fuelEconomy = EnergyStats(economyIntervals(entries, EntryType.FUEL).filter { inPeriod(it.endEpochDay) }),
            chargeEconomy = EnergyStats(economyIntervals(entries, EntryType.CHARGE).filter { inPeriod(it.endEpochDay) }),
        )
    }

    /**
     * Costs per calendar month for [months] months ending with the month of [lastEpochDay],
     * oldest first. Months without entries are included with zero cost.
     */
    fun monthlyCosts(entries: List<LogEntry>, lastEpochDay: Long, months: Int): List<MonthCost> {
        val last = YearMonth.from(LocalDate.ofEpochDay(lastEpochDay))
        val first = last.minusMonths((months - 1).toLong())
        val energy = DoubleArray(months)
        val other = DoubleArray(months)
        for (e in entries) {
            val ym = YearMonth.from(LocalDate.ofEpochDay(e.dateEpochDay))
            val index = (ym.year - first.year) * 12 + (ym.monthValue - first.monthValue)
            if (index < 0 || index >= months) continue
            if (e.type == EntryType.EXPENSE) other[index] += e.totalCost else energy[index] += e.totalCost
        }
        val result = ArrayList<MonthCost>(months)
        for (i in 0 until months) {
            val ym = first.plusMonths(i.toLong())
            result.add(MonthCost(ym.year, ym.monthValue, energy[i], other[i]))
        }
        return result
    }

    /** Odometer of the most recent entry that has one, or null. */
    fun latestOdometerKm(entries: List<LogEntry>): Double? =
        entries.filter { it.odometerKm != null }.maxWithOrNull(CHRONOLOGICAL)?.odometerKm

    /** Highest odometer reading dated on or before [epochDay], ignoring the entry [exceptId]. */
    fun highestOdometerUntil(entries: List<LogEntry>, epochDay: Long, exceptId: Long): Double? {
        var best: Double? = null
        for (e in entries) {
            val odo = e.odometerKm ?: continue
            if (e.id == exceptId || e.dateEpochDay > epochDay) continue
            val current = best
            if (current == null || odo > current) best = odo
        }
        return best
    }
}
