package teamparkinglot.parkinggo.parking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParkingCond {

    private String region;
    private LocalDateTime parking_start_time;
    private LocalDateTime parking_end_time;
    private String sort;
    private String crtLocation;
}
