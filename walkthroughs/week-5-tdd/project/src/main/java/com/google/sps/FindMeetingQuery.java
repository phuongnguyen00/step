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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

public final class FindMeetingQuery {
  public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {
    // Assume that one person does not have two meetings at the same time

    // If no attendees, then the whole day is available for the request (assume duration <= 1 day)
    ArrayList<TimeRange> availableSlots = new ArrayList<TimeRange>();

    if (request.getAttendees().isEmpty() && request.getOptionalAttendees().isEmpty()){
        availableSlots.add(TimeRange.WHOLE_DAY);
        return availableSlots;
    }
    
    // CalendarAttendees is a lookup table where each person's name is the key
    // and the associated values are the times in the day when they are busy

    // Step 1: Store the information of the mandatory attenddees provided in events
    HashMap<String, ArrayList<TimeRange>> calendarAttendees = createCommonCalendar(events);

    // Step 2: Find all occupied slots of relevent people based on the request
    ArrayList<TimeRange> allOccupiedSlots = getAllOccupiedSlots(calendarAttendees, request.getAttendees());

    // Step 3: Find all possible windows by merging occupied ones and fine the (inverse selection) of those times
    System.out.println("The allOccupiedSlots is: " + allOccupiedSlots);
    Collections.sort(allOccupiedSlots, TimeRange.ORDER_BY_START);
    System.out.println("The allOccupiedSlots after being sorted by start is: " + allOccupiedSlots);

    System.out.println("START LOOKING FROM HERE");
    allOccupiedSlots = getOverlappedSlots(allOccupiedSlots);
    System.out.println("The allOccupiedSlots after checking for overlapped is: " + allOccupiedSlots);

    ArrayList<TimeRange> allAvailableSlots = getInverseSlots(allOccupiedSlots);
    System.out.println("The allAvailableSlots after getting the inverse is: " + allAvailableSlots);

    // Step 4: Get all possible windows with the correct duration (duration >= duration in request)

    for (int i = 0; i < allAvailableSlots.size(); i++) {
        if (allAvailableSlots.get(i).duration() >= request.getDuration()) {
            availableSlots.add(allAvailableSlots.get(i));
        }
    }

    // Only consider optional attendees when all of mandatory attendees and optionala attendees are available 
    // Step 5: Get the slots that work for optional attendees and check if any of them overlap with 
    // available slots for mandatory attendees. If yes, then return available slots. 

    System.out.println("OPTIONAL RANGE");
    // Step 5a: Find all occupied slots of optional attendees in the common calendar
    ArrayList<TimeRange> allOccupiedOptional = 
            getAllOccupiedSlots(calendarAttendees, request.getOptionalAttendees());
    System.out.println("Optional attendees are: " + request.getOptionalAttendees());
    System.out.println("The allOccupiedOptional is: " + allOccupiedOptional);

    // Step 5b: Condense the occupied slots (merge overlapping ones) after sorting all entries
    Collections.sort(allOccupiedOptional, TimeRange.ORDER_BY_START);
    allOccupiedOptional = getOverlappedSlots(allOccupiedOptional);
    
    System.out.println("The allOccupiedOptional after checking for overlapped is: " +     allOccupiedOptional);

    // Step 5c: Get the available slots for optional attendees
    ArrayList<TimeRange> availableOptional = getInverseSlots(allOccupiedOptional);
    System.out.println("The availableOptional list of optional attendees are: " + availableOptional);
    if (request.getAttendees().isEmpty()) {
        ArrayList<TimeRange> availableOptionalOnly = TimeRange.getRangesLongEnough(availableOptional, (int)request.getDuration());
        return availableOptionalOnly;
    }
    
    // Step 5d: Find all possible open slots by comparing the optional occupied slots and the free slots of mandatory attendees
    ArrayList<TimeRange> availableWithOptional = new ArrayList<TimeRange>();

    for (int i = 0; i < availableSlots.size(); i++){
        for (int j = 0; j < availableOptional.size(); j++) {
            TimeRange availableSlot = availableSlots.get(i);
            TimeRange availableOptionalSlot = availableOptional.get(j);

            // If there is no overlap, then doesn't care about optional available slots
            System.out.println("Consider the case of: availableSlot - " + availableSlot + " and availableOptionalSlot: " + availableOptionalSlot);
            if (availableSlot.overlaps(availableOptionalSlot)) {
                if (availableOptionalSlot.contains(availableSlot)) {
                    availableWithOptional.add(availableSlot);
                    System.out.println("Enter first if statement: optional contains available");
                } else if (availableSlot.contains(availableOptionalSlot) && !availableSlot.equals(availableOptionalSlot)) {
                    if (availableOptionalSlot.hasEnoughTime((int)request.getDuration())) 
                           {availableWithOptional.add(availableOptionalSlot);}
                    System.out.println("Enter second if statement: available contains optional");

                } else { // Two slots overlap but one does not contain another
                    System.out.println("Enter third if statement: overlap but no containment");
                    TimeRange newSlot = availableSlot.getIntersection(availableOptionalSlot);
                    if (newSlot.hasEnoughTime((int)request.getDuration())) 
                           {availableWithOptional.add(newSlot);}
                }
            // If there is no current overlap with the optional slot and no future overlaps, 
            // then no overlap with next optional slots in this iteration
            } else {
                if (availableSlot.start() < availableOptionalSlot.start()) { 
                    System.out.println("No overlap -> break out of inner for loop");
                    break;
                } else { //There maybe future overlaps
                    System.out.println("No overlap now but maybe in the future -> next iteration of inner for loop");
                    continue;
                }
            }
        }
    }

    // Step 5e: If there are slots that work for all mandatory and optional attendees, then return 
    System.out.println("Available times wiht optional people in mind: " + availableWithOptional);
    if (!availableWithOptional.isEmpty()) return getUniqueSortedSlots(availableWithOptional);

    // Make sure the returned arrayList is unique
    return getUniqueSortedSlots(availableSlots);
  }
  
  /**
  * Create a common calendar that stores all occcupied time slots of people from a collection of events
  */
  private HashMap<String, ArrayList<TimeRange>> createCommonCalendar(Collection<Event> events) {
    HashMap<String, ArrayList<TimeRange>> calendarAttendees = new HashMap<String, ArrayList<TimeRange>>(); 

    for (Event event: events) {
        for (String attendee: event.getAttendees()){
            if (calendarAttendees.containsKey(attendee)) {
                calendarAttendees.get(attendee).add(event.getWhen());
            } else {
                ArrayList<TimeRange> occupiedSlots = new ArrayList<TimeRange>();
                occupiedSlots.add(event.getWhen());
                calendarAttendees.put(attendee, occupiedSlots);
            }
        }
    }

    return calendarAttendees;
  }

  /**
  * @return an arrayList of all occupied slots from a common calendar of attendees
  */
  private ArrayList<TimeRange> getAllOccupiedSlots(HashMap<String, ArrayList<TimeRange>> calendar, Collection<String> attendees){
    ArrayList<TimeRange> allOccupiedSlots = new ArrayList<TimeRange>();
    for (String attendee: attendees){
        if (calendar.containsKey(attendee)) {
            ArrayList<TimeRange> occupiedSlots = calendar.get(attendee);
            for (int i = 0; i < occupiedSlots.size(); i++) {
                allOccupiedSlots.add(occupiedSlots.get(i));
            }
        } else {// The attendee is not in the database yet, which means this attendee is assumed to be free all day
            allOccupiedSlots.add(TimeRange.fromStartEnd(0, 0, false));
        } 
    }
    return allOccupiedSlots;
  }
  
  /**
  * @param an ArrayList of timeslots sorted by start, can be occupied or unoccupied
  * @return all overlap time slots 
  */
  private ArrayList<TimeRange> getOverlappedSlots(ArrayList<TimeRange> timeSlots) {
      ArrayList<TimeRange> overlappedSlots = new ArrayList<TimeRange>();
      if (timeSlots.isEmpty()){
          return overlappedSlots;
      }

      // Start from the first time slot, return the most concise treeset of time slots (that are either occupied or not)
      TimeRange overlappedSlot = timeSlots.get(0);

      if (timeSlots.size() == 1) {
          overlappedSlots.add(overlappedSlot);
          return overlappedSlots;
      }
      
      boolean inclusive = false;
      // There are at least two things in timeSlots
      for (int i = 1; i < timeSlots.size(); i++){

          if (timeSlots.get(i).overlaps(overlappedSlot)){
              if (timeSlots.get(i).end() == TimeRange.END_OF_DAY) {inclusive = true;}
              overlappedSlot = overlappedSlot.mergeOverlapped(timeSlots.get(i), inclusive);
              if (i == timeSlots.size() - 1) {// If this is the last time slot
                    overlappedSlots.add(overlappedSlot);
              }
          } else {
              overlappedSlots.add(overlappedSlot);
              overlappedSlot = timeSlots.get(i);
              if (i == timeSlots.size() - 1) {
                  overlappedSlots.add(overlappedSlot);
              }
          }
      }

      return overlappedSlots;
  }

  /**
  * @param timeSlots an arrayList of TimeRanges that are sorted whose elements do not overlap (has gone through getOverlappedSlots)
  * @return the other slots in a day
  */
  private ArrayList<TimeRange> getInverseSlots(ArrayList<TimeRange> timeSlots){
       
       ArrayList<TimeRange> otherSlots = new ArrayList<TimeRange>();
       if (timeSlots.isEmpty()) {return otherSlots;}

       // Assume that timeSlots only get the most concise slots, and the last slot also ends the latest
    
       if (timeSlots.get(0).start() > TimeRange.START_OF_DAY) {
           otherSlots.add(TimeRange.fromStartEnd(TimeRange.START_OF_DAY, timeSlots.get(0).start(), false));
       }

       // All elements in timeSlots do not overlap
       for (int i = 0; i < timeSlots.size() - 1; i++) {
           System.out.println("The timeslots being considered are: " + timeSlots.get(i) + " and " + timeSlots.get(i+1));
           System.out.println("The result should be: " + timeSlots.get(i).end() + ", " + timeSlots.get(i+1).start()); 
           TimeRange otherSlot = TimeRange.fromStartEnd(timeSlots.get(i).end(), timeSlots.get(i+1).start(), false);
           otherSlots.add(otherSlot);
       }
    
       // The order is reserved
       if (timeSlots.get(timeSlots.size()-1).end() < TimeRange.END_OF_DAY) {
           otherSlots.add(TimeRange.fromStartEnd(timeSlots.get(timeSlots.size()-1).end(), TimeRange.END_OF_DAY, true));
       }

       return otherSlots;
  }
  
  /**
  * @param allSlots: an arrayList of time ranges
  * @return an arrayList of unique time ranges sorted in ascending order (ORDER_BY_START)
  */
  private ArrayList<TimeRange> getUniqueSortedSlots(ArrayList<TimeRange> allSlots){
    Set<TimeRange> uniqueSlots = new HashSet<TimeRange>(allSlots);
    allSlots.clear();
    allSlots.addAll(uniqueSlots);
    Collections.sort(allSlots, TimeRange.ORDER_BY_START);
    return allSlots;
  }

}
