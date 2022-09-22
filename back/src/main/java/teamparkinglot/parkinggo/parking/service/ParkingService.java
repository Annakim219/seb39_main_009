package teamparkinglot.parkinggo.parking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import teamparkinglot.parkinggo.exception.BusinessException;
import teamparkinglot.parkinggo.exception.ExceptionCode;
import teamparkinglot.parkinggo.history.History;
import teamparkinglot.parkinggo.history.HistoryRepository;
import teamparkinglot.parkinggo.member.entity.Member;
import teamparkinglot.parkinggo.member.repository.MemberRepository;
import teamparkinglot.parkinggo.member.service.MemberService;
import teamparkinglot.parkinggo.parking.dto.*;
import teamparkinglot.parkinggo.parking.entity.Parking;
import teamparkinglot.parkinggo.parking.mapper.ParkingMapper;
import teamparkinglot.parkinggo.parking.repository.ParkingQueryDsl;
import teamparkinglot.parkinggo.parking.repository.ParkingRepository;
import teamparkinglot.parkinggo.parking_place.ParkingPlace;
import teamparkinglot.parkinggo.parking_place.ParkingPlaceRepository;
import teamparkinglot.parkinggo.reservation.entity.Reservation;
import teamparkinglot.parkinggo.reservation.repository.ReservationRepository;
import teamparkinglot.parkinggo.reservation.service.ReservationService;
import teamparkinglot.parkinggo.review.entity.Review;
import teamparkinglot.parkinggo.review.repository.ReviewRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParkingService {

    private final ParkingRepository parkingRepository;
    private final ParkingQueryDsl parkingQueryDsl;
    private final ReservationService reservationService;
    private final HistoryRepository historyRepository;
    private final MemberService memberService;
    private final ReviewRepository reviewRepository;
    private final MemberRepository memberRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingPlaceRepository parkingPlaceRepository;

    private final ParkingMapper parkingMapper;

    public List<Parking> findByCond(ParkingCondDto parkingCondDto) {

        LocalDateTime dtoStartTime = parkingCondDto.getParkingStartTime();
        LocalDateTime dtoEndTime = parkingCondDto.getParkingEndTime();

        List<Parking> byRegion = parkingQueryDsl.findByRegion(parkingCondDto.getRegion());

        List<ParkingReserv> reservationList = byRegion.stream()
                .map(e -> new ParkingReserv(e.getId(), reservationService.findByParkingId(e.getId())))
                .collect(Collectors.toList());

        List<Long> realParking = new ArrayList();

        for (ParkingReserv parkingReserv : reservationList) {
            boolean flag = true;

            List<Reservation> reservations = parkingReserv.getReservations();

            for (Reservation reservation : reservations) {
                LocalDateTime reservStart = reservation.getParkingStartDateTime();
                LocalDateTime reservEndTime = reservation.getParkingEndDateTime();

                if (!(dtoStartTime.isBefore(reservStart) || dtoStartTime.isAfter(reservEndTime) || dtoStartTime.isEqual(reservEndTime))
                && !(dtoEndTime.isBefore(reservStart) || dtoEndTime.isAfter(reservEndTime) || dtoEndTime.isEqual(reservStart))) {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                realParking.add(parkingReserv.getParkingId());
            }
        }

        parkingRepository.findAllByid(realParking);

        return realParking.stream()
                .map(e -> parkingRepository.findById(e).orElseThrow(
                        () -> new BusinessException(ExceptionCode.SEARCH_ERROR)
                ))
                .collect(Collectors.toList());
    }

    public List<ParkingRecentDto> findRecentSearches(String email) {

        if (email == null) {
            return new ArrayList<>();
        }

        List<History> histories = historyRepository.findByMemberEmail(email);

        return histories.stream()
                .map(e -> new ParkingRecentDto(e.getParking().getName(), e.getParking().getAddress().getParcel()))
                .collect(Collectors.toList());
    }

    /**
     * 주차장 기본 검색
     * @param id
     */
    public Parking findById(Long id) {
        return parkingRepository.findById(id).orElseThrow(
                () -> new BusinessException(ExceptionCode.PARKING_NOT_EXISTS)
        );
    }

    public Parking findVerifiedParking(long id) {
        return parkingRepository.findById(id).orElseThrow(
                () -> new BusinessException(ExceptionCode.PARKING_NOT_EXISTS)
        );
    }

    public ParkingMapDto findMap(long id) {

        Parking parking = findVerifiedParking(id);
        List<ValidNum> validNums = reservationService.findByParkingId(id).stream()
                .map(e -> new ValidNum(e.getParkingPlace().getNumber()))
                .collect(Collectors.toList());

        return new ParkingMapDto(parking.getParkingMap(), validNums);
    }

    public CreateReservDto createReservation(Long id, ParkingDateTimeDto parkingDateTimeDto, String email) {
        Parking parking = parkingRepository.findById(id).orElseThrow(
                () -> new BusinessException(ExceptionCode.PARKING_NOT_EXISTS)
        );
        Member member = memberRepository.findByEmail(email).orElseThrow(
                () -> new BusinessException(ExceptionCode.MEMBER_NOT_EXISTS)
        );
        ParkingPlace parkingPlace = parkingPlaceRepository.findParkingPlace(id, parkingDateTimeDto.getNumber());

        long time = ((parkingDateTimeDto.getParkingEndDateTime().getHour() * 60) + parkingDateTimeDto.getParkingEndDateTime().getMinute())
                        -((parkingDateTimeDto.getParkingStartDateTime().getHour() * 60) + parkingDateTimeDto.getParkingStartDateTime().getMinute());

//        long time = ChronoUnit.MINUTES.between(parkingDateTimeDto.getParkingStartDateTime(), parkingDateTimeDto.getParkingEndDateTime());

        long price = ((time / 30) * parking.getPrice());

        if(price >= parking.getDayMax()) price = parking.getDayMax();

        Reservation reservation = Reservation.builder()
                .reservationDate(LocalDateTime.now())
                .parkingStartDateTime(parkingDateTimeDto.getParkingStartDateTime())
                .parkingEndDateTime(parkingDateTimeDto.getParkingEndDateTime())
                .member(member)
                .parkingPlace(parkingPlace)
                .payOrNot(false)
                .price(price)
                .refundAgmt(true)
                .build();

        reservationRepository.save(reservation);

        return parkingMapper.reservationToCreateReservDto(reservation, member);
    }
}
