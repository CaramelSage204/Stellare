package com.example.data.seed

import com.example.data.local.UserRole
import com.example.data.local.entity.*
import com.example.data.repository.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.first

class DemoDataSeeder(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val reviewRepository: ReviewRepository,
    private val appointmentRepository: AppointmentRepository,
    private val walletRepository: WalletRepository
) {
    suspend fun prepopulateMockDataIfNeeded() {
        val existing = userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST).first()
        if (existing.isNotEmpty()) return

        // 1. Insert Mock Psychologists
        val p1Id = userRepository.insertUser(UserEntity(
            firstName = "Anna",
            lastName = "Nowak",
            age = 34,
            gender = "Kobieta",
            phone = "+48 501 234 567",
            email = "anna.nowak@wektor.pl",
            role = UserRole.PSYCHOLOGIST,
            isVerified = true,
            qualifications = "Magister Psychologii UJ",
            specializations = "Rodzina, Depresja, Lęki",
            pricePerSession = 150.0,
            rating = 4.8,
            ratingCount = 8,
            bio = "Jestem psychologiem z 8-letnim doświadczeniem w pracy z rodzinami oraz osobami cierpiącymi na depresję i stany lękowe. Pomagam odnaleźć wewnętrzny wektor rozwoju.",
            isCurrentUser = false
        )).toInt()

        val p2Id = userRepository.insertUser(UserEntity(
            firstName = "Igor",
            lastName = "Kowalski",
            age = 24,
            gender = "Mężczyzna",
            phone = "+48 602 345 678",
            email = "igor.k@student.pl",
            role = UserRole.PSYCHOLOGY_STUDENT,
            isVerified = false,
            qualifications = "Student 4. roku UW",
            specializations = "Ogólny, Praca, Stres",
            pricePerSession = 80.0,
            rating = 4.2,
            ratingCount = 3,
            bio = "Pasjonat psychologii klinicznej. Jako student oferuję niedrogie konsultacje, wspierając w radzeniu sobie ze stresem zawodowym i akademickim.",
            isCurrentUser = false
        )).toInt()

        val p3Id = userRepository.insertUser(UserEntity(
            firstName = "Maria",
            lastName = "Wiśniewska",
            age = 29,
            gender = "Kobieta",
            phone = "+48 703 456 789",
            email = "m.wisniewska@phd.pl",
            role = UserRole.PSYCHOLOGIST,
            isVerified = true,
            qualifications = "Doktorantka SWPS",
            specializations = "Życie intymne, Relacje, Emocje",
            pricePerSession = 180.0,
            rating = 4.9,
            ratingCount = 12,
            bio = "Specjalizuję się w psychoterapii relacji i sfery intymnej. Prowadzę badania nad dynamiką emocjonalną w związkach partnerskich.",
            isCurrentUser = false
        )).toInt()

        val p4Id = userRepository.insertUser(UserEntity(
            firstName = "Piotr",
            lastName = "Zieliński",
            age = 42,
            gender = "Mężczyzna",
            phone = "+48 804 567 890",
            email = "p.zielinski@terapia.pl",
            role = UserRole.PSYCHOLOGIST,
            isVerified = true,
            qualifications = "Certyfikowany Psychoterapeuta PTTPB",
            specializations = "Uzależnienia, Rodzina, Kryzysy",
            pricePerSession = 220.0,
            rating = 4.5,
            ratingCount = 5,
            bio = "Certyfikowany terapeuta poznawczo-behawioralny. Pomagam przejść przez najtrudniejsze kryzysy życiowe i uzależnienia.",
            isCurrentUser = false
        )).toInt()

        // 2. Insert Mock Patients (with concern descriptions)
        val pat1Id = userRepository.insertUser(UserEntity(
            firstName = "Janusz",
            lastName = "Kowal",
            age = 28,
            gender = "Mężczyzna",
            phone = "+48 905 678 901",
            email = "janusz.it@poczta.pl",
            role = UserRole.PATIENT,
            bio = "Szukam wsparcia w związku z wypaleniem zawodowym w IT oraz przewlekłym stresem.",
            isCurrentUser = false
        )).toInt()

        val pat2Id = userRepository.insertUser(UserEntity(
            firstName = "Julia",
            lastName = "Malinowska",
            age = 21,
            gender = "Kobieta",
            phone = "+48 106 789 012",
            email = "julia.m@stud.pl",
            role = UserRole.PATIENT,
            bio = "Zmagam się ze stanami lękowymi przed egzaminami oraz trudnościami w relacjach rówieśniczych.",
            isCurrentUser = false
        )).toInt()

        // 3. Insert Reviews for Anna Novak
        reviewRepository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Tomasz B.", rating = 5, comment = "Bardzo profesjonalna pomoc. Sesje pomogły mi odbudować relacje z synem. Gorąco polecam!"))
        reviewRepository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Anna K.", rating = 5, comment = "Ciepła, empatyczna i niezwykle merytoryczna pani psycholog."))
        reviewRepository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Krzysztof", rating = 4, comment = "Dobra komunikacja i konkretne porady, chociaż ceny mogłyby być nieco niższe."))
        reviewRepository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Karolina", rating = 5, comment = "Pani Anna uratowała nasze małżeństwo! Wspaniała praca nad komunikacją."))
        reviewRepository.insertReview(ReviewEntity(psychologistId = p1Id, reviewerName = "Marek S.", rating = 4, comment = "Rzeczowa pomoc w kryzysie zawodowym. Polecam każdemu."))

        // Reviews for Maria Wiśniewska
        reviewRepository.insertReview(ReviewEntity(psychologistId = p3Id, reviewerName = "Zofia", rating = 5, comment = "Cudowna atmosfera! Pani Maria potrafi otworzyć nawet najbardziej skrytą osobę."))
        reviewRepository.insertReview(ReviewEntity(psychologistId = p3Id, reviewerName = "Robert", rating = 5, comment = "Niezwykłe wyczucie tematów intymnych. Czuję ogromną ulgę po sesjach."))

        // 4. Create Initial Chat and Message history
        val chat1Id = chatRepository.createChat(ChatEntity(
            psychologistId = p1Id,
            patientId = pat1Id,
            lastMessage = "Czekam na nasze kolejne spotkanie w środę.",
            lastMessageTime = System.currentTimeMillis() - 3600000
        )).toInt()

        chatRepository.insertMessage(MessageEntity(chatId = chat1Id, senderId = pat1Id, text = "Dzień dobry, mam problem z zasypianiem przez stres w biurze.", timestamp = System.currentTimeMillis() - 7200000))
        chatRepository.insertMessage(MessageEntity(chatId = chat1Id, senderId = p1Id, text = "Rozumiem Januszu. Na początek wypróbujmy technikę oddechową 4-7-8 przed snem.", timestamp = System.currentTimeMillis() - 5400000))
        chatRepository.insertMessage(MessageEntity(chatId = chat1Id, senderId = pat1Id, text = "Przetestowałem ją, jest lekka poprawa. Chciałbym omówić to na sesji.", timestamp = System.currentTimeMillis() - 4500000))
        chatRepository.insertMessage(MessageEntity(chatId = chat1Id, senderId = p1Id, text = "Czekam na nasze kolejne spotkanie w środę.", timestamp = System.currentTimeMillis() - 3600000))

        // Create some default notes for this client chat (Zametki)
        chatRepository.insertNote(NoteEntity(chatId = chat1Id, title = "Sesja 1: Wypalenie i sen", content = "Pacjent Janusz skarży się na bezsenność spowodowaną deadline'ami. Praca w korporacji IT. Zaproponowano technikę 4-7-8 i ograniczenie ekranów przed snem."))
        chatRepository.insertNote(NoteEntity(chatId = chat1Id, title = "Plan działania", content = "1. Praktyka mindfullness 10 min dziennie.\n2. Rozmowa o granicach w pracy z managerem.\n3. Monitorowanie jakości snu."))

        // 5. Prepopulate Calendar appointments
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        
        // Helper function to get date relative to today
        fun getRelativeDate(daysOffset: Int): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, daysOffset)
            return sdf.format(cal.time)
        }

        // Future booked appointment
        appointmentRepository.insertAppointment(
            AppointmentEntity(
                psychologistId = p1Id,
                patientId = pat1Id,
                date = getRelativeDate(1),
                time = "10:00",
                notes = "Kontynuacja tematu bezsenności i technik oddechowych.",
                status = "BOOKED"
            )
        )
        // Future free slots for Anna Nowak
        appointmentRepository.insertAppointment(AppointmentEntity(psychologistId = p1Id, date = getRelativeDate(1), time = "12:00", status = "FREE"))
        appointmentRepository.insertAppointment(AppointmentEntity(psychologistId = p1Id, date = getRelativeDate(1), time = "15:30", status = "FREE"))
        appointmentRepository.insertAppointment(AppointmentEntity(psychologistId = p1Id, date = getRelativeDate(2), time = "09:00", status = "FREE"))
        appointmentRepository.insertAppointment(AppointmentEntity(psychologistId = p1Id, date = getRelativeDate(2), time = "11:00", status = "FREE"))

        // Booked appointment for Anna Nowak with Julia Malinowska
        appointmentRepository.insertAppointment(
            AppointmentEntity(
                psychologistId = p1Id,
                patientId = pat2Id,
                date = getRelativeDate(3),
                time = "14:00",
                notes = "Praca z lękiem przedegzaminacyjnym.",
                status = "BOOKED"
            )
        )

        // Completed appointment
        appointmentRepository.insertAppointment(
            AppointmentEntity(
                psychologistId = p1Id,
                patientId = pat1Id,
                date = getRelativeDate(-2),
                time = "15:00",
                notes = "Sesja wstępna. Omówienie kontraktu terapeutycznego.",
                status = "COMPLETED"
            )
        )

        // Free slots for other psychologists
        appointmentRepository.insertAppointment(AppointmentEntity(psychologistId = p2Id, date = getRelativeDate(1), time = "10:00", status = "FREE"))
        appointmentRepository.insertAppointment(AppointmentEntity(psychologistId = p2Id, date = getRelativeDate(1), time = "13:00", status = "FREE"))
        appointmentRepository.insertAppointment(AppointmentEntity(psychologistId = p3Id, date = getRelativeDate(2), time = "16:00", status = "FREE"))
    }
}
