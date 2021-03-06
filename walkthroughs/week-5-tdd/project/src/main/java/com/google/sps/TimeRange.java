// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.Collections;

/**
 * Class representing a span of time, enforcing properties (e.g. start comes before end) and
 * providing methods to make ranges easier to work with (e.g. {@code overlaps}).
 */
public final class TimeRange {
  public static final int START_OF_DAY = getTimeInMinutes(0, 0);
  public static final int END_OF_DAY = getTimeInMinutes(23, 59);

  public static final TimeRange WHOLE_DAY = new TimeRange(0, 24 * 60);

  /**
   * A comparator for sorting ranges by their start time in ascending order.
   */
  public static final Comparator<TimeRange> ORDER_BY_START = new Comparator<TimeRange>() {
    @Override
    public int compare(TimeRange a, TimeRange b) {
      return Long.compare(a.start, b.start);
    }
  };

  /**
   * A comparator for sorting ranges by their end time in ascending order.
   */
  public static final Comparator<TimeRange> ORDER_BY_END = new Comparator<TimeRange>() {
    @Override
    public int compare(TimeRange a, TimeRange b) {
      return Long.compare(a.end(), b.end());
    }
  };

  private final int start;
  private final int duration;

  private TimeRange(int start, int duration) {
    this.start = start;
    this.duration = duration;
  }

  /**
   * Returns the start of the range in minutes.
   */
  public int start() {
    return start;
  }

  /**
   * Returns the number of minutes between the start and end.
   */
  public int duration() {
    return duration;
  }

  /**
   * Returns the end of the range. This ending value is the closing exclusive bound.
   */
  public int end() {
    return start + duration;
  }

  /**
   * Checks if two ranges overlap. This means that at least some part of one range falls within the
   * bounds of another range.
   */
  public boolean overlaps(TimeRange other) {
    // For two ranges to overlap, one range must contain the start of another range.
    //
    // Case 1: |---| |---|
    //
    // Case 2: |---|
    //            |---|
    //
    // Case 3: |---------|
    //            |---|
    return this.contains(other.start) || other.contains(this.start);
  }

  /**
  * @return a timeRange that ends later than the other one 
  */
  private TimeRange endLater(TimeRange other){
      return (this.end() >= other.end()) ? this : other;
  }

  /**
   * Precondition: TimeRange other (parameter) always starts at the same time as or later than this TimeRange
   * @param other a TimeRange that is overlapped with the current TimeRange
   *        inclusive if one of the Time Ranges is also the end 
   * @return a TimeRange that encompasses the two TimeRanges
   */
  public TimeRange mergeOverlapped(TimeRange other, boolean inclusive){
    if (this.contains(other)) {return this;} 
    else if (other.contains(this)) {return other;}
    else {// No TimeRange includes the whole other TimeRange
        return fromStartEnd(this.start(), endLater(other).end(), inclusive);
    }
  }

  /**
   * Checks if this range completely contains another range. This means that {@code other} is a
   * subset of this range. This is an inclusive bounds, meaning that if two ranges are the same,
   * they contain each other.
   */
  public boolean contains(TimeRange other) {
    // If this range has no duration, it cannot contain anything.
    if (duration <= 0) {
      return false;
    }

    // If the other range has no duration, then we must treat it like a point in time rather than a
    // range.
    if (other.duration <= 0) {
      return contains(this, other.start);
    }

    // We need the inclusive end for this check in order for this case to equal true:
    // |------|
    //     |--|
    int otherInclusiveEnd = other.start + other.duration - 1;
    return contains(this, other.start) && contains(this, otherInclusiveEnd);
  }

  public boolean contains(int point) {
    return contains(this, point);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TimeRange && equals(this, (TimeRange) other);
  }

  @Override
  public int hashCode() {
    return Long.hashCode(start) ^ Long.hashCode(duration);
  }

  @Override
  public String toString() {
    return String.format("Range: [%d, %d)", start, start + duration);
  }

  private static boolean contains(TimeRange range, int point) {
    // If a range has no duration, it cannot contain anything.
    if (range.duration <= 0) {
      return false;
    }

    // If the point comes before the start of the range, the range cannot contain it.
    if (point < range.start) {
      return false;
    }

    // If the point is on the end of the range. We don't count it as included in the range. For
    // example, if we have a range that starts at 8:00 and is 30 minutes long, it would end at 8:30.
    // But that range should on contain 8:30 because it would end just before 8:30 began.
    return point < range.start + range.duration;
  }

  private static boolean equals(TimeRange a, TimeRange b) {
    return a.start == b.start && a.duration == b.duration;
  }

  public static int getTimeInMinutes(int hours, int minutes) {
    if (hours < 0 || hours >= 24) {
      throw new IllegalArgumentException("Hours can only be 0 through 23 (inclusive).");
    }

    if (minutes < 0 || minutes >= 60) {
      throw new IllegalArgumentException("Minutes can only be 0 through 59 (inclusive).");
    }

    return (hours * 60) + minutes;
  }

  /**
   * Creates a {@code TimeRange} from {@code start} to {@code end}. Whether or not {@code end} is
   * included in the range will depend on {@code inclusive}. If {@code inclusive} is {@code true},
   * then @{code end} will be in the range.
   */
  public static TimeRange fromStartEnd(int start, int end, boolean inclusive) {
    return inclusive ? new TimeRange(start, end - start + 1) : new TimeRange(start, end - start);
  }

  /**
   * Create a {@code TimeRange} starting at {@code start} with a duration equal to {@code duration}.
   */
  public static TimeRange fromStartDuration(int start, int duration) {
    return new TimeRange(start, duration);
  }
  
  /**
  * Check if one event starts before another
  */
  public boolean precedes(TimeRange other){
      return this.start() <= other.start();
  }

  /**
  * Check if a time range is long enough
  */
  public boolean hasEnoughTime(int duration) {
      return this.duration() >= duration;
  }

  /**
  * @return an arrayList of all time ranges that are long enough for a list of time ranges
  */
  public static ArrayList<TimeRange> getRangesLongEnough(ArrayList<TimeRange> someTimeRanges, int duration){
      ArrayList<TimeRange> longEnoughRanges = new ArrayList<TimeRange>();
      for (int i = 0; i < someTimeRanges.size(); i++){
          if (someTimeRanges.get(i).hasEnoughTime(duration)) {
              longEnoughRanges.add(someTimeRanges.get(i));
          }
      }
      return longEnoughRanges;
  }
  
  /**
  * Get the intersection of two overlapping time ranges. One does not contain another. See below example
  *  |-----|
  *     |------|
  */
  public TimeRange getIntersection(TimeRange other) {
      if (this.precedes(other)) {
          boolean inclusive = (this.end() == TimeRange.END_OF_DAY);
          return TimeRange.fromStartEnd(other.start(), this.end(), inclusive);
      } else {
          boolean inclusive = (this.end() == TimeRange.END_OF_DAY);
          return TimeRange.fromStartEnd(this.start(), other.end(), inclusive);
      }
  }

  /**
  * @param allSlots: an arrayList of time ranges
  * @return an arrayList of unique time ranges sorted in ascending order (ORDER_BY_START)
  */
  public static ArrayList<TimeRange> getUniqueSortedSlots(ArrayList<TimeRange> allSlots){
    Set<TimeRange> uniqueSlots = new HashSet<TimeRange>(allSlots);
    allSlots.clear();
    allSlots.addAll(uniqueSlots);
    Collections.sort(allSlots, TimeRange.ORDER_BY_START);
    return allSlots;
  }
  
}
