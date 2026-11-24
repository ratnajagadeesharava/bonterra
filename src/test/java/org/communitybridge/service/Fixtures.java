package org.communitybridge.service;

/**
 * The synthetic seed data the reset command must create (spec: Seed fixtures). Tests assert against
 * these values, so the seed implemented in step 4 has to match them exactly.
 *
 * <pre>
 * Community Bridge (np_cb)
 *   staff:        Priya Sharma  stf_cb_case    CASE_WORKER
 *                 Arjun Patel   stf_cb_pm      PROGRAM_MANAGER
 *                 Neha Gupta    stf_cb_fin     FINANCE
 *                 Rahul Verma   stf_cb_former  CASE_WORKER (inactive)
 *   funds:        fnd_cb_housing  Housing Stability     $10,000.00  ACTIVE
 *                 fnd_cb_small    Small Emergencies        $500.00  ACTIVE
 *                 fnd_cb_closed   Winter Relief 2025     $1,000.00  CLOSED
 *   participants: par_cb_001 Kavya Reddy (3, Hyderabad)
 *                 par_cb_002 Rohan Mehta (1, Warangal)
 *                 par_cb_003 Sneha Iyer  (4, Vijayawada)
 *
 * Harbor Relief (np_hr)
 *   staff:        Ananya Das    stf_hr_case    CASE_WORKER
 *                 Vikram Singh  stf_hr_pm      PROGRAM_MANAGER
 *                 Isha Nair     stf_hr_fin     FINANCE
 *   funds:        fnd_hr_utility  Utility Relief       $5,000.00  ACTIVE
 *   participants: par_hr_001 Aditya Rao     (2, Chennai)
 *                 par_hr_002 Meghana Pillai (5, Kochi)
 * </pre>
 *
 * No requests, payments or audit events exist after a reset; every fund starts at 0 committed, 0 paid.
 */
public final class Fixtures {

    public static final String NP_CB = "np_cb";
    public static final String NP_CB_NAME = "Community Bridge";
    public static final String NP_HR = "np_hr";
    public static final String NP_HR_NAME = "Harbor Relief";

    // Community Bridge staff
    public static final String PRIYA = "stf_cb_case";
    public static final String ARJUN = "stf_cb_pm";
    public static final String NEHA = "stf_cb_fin";
    public static final String RAHUL_INACTIVE = "stf_cb_former";

    // Harbor Relief staff
    public static final String ANANYA = "stf_hr_case";
    public static final String VIKRAM = "stf_hr_pm";
    public static final String ISHA = "stf_hr_fin";

    // Community Bridge funds
    public static final String HOUSING = "fnd_cb_housing";
    public static final long HOUSING_CENTS = 1_000_000L;
    public static final String SMALL = "fnd_cb_small";
    public static final long SMALL_CENTS = 50_000L;
    public static final String CLOSED = "fnd_cb_closed";
    public static final long CLOSED_CENTS = 100_000L;

    // Harbor Relief fund
    public static final String HR_UTILITY = "fnd_hr_utility";
    public static final long HR_UTILITY_CENTS = 500_000L;

    // Participants
    public static final String KAVYA = "par_cb_001";
    public static final String ROHAN = "par_cb_002";
    public static final String SNEHA = "par_cb_003";
    public static final String ADITYA = "par_hr_001";
    public static final String MEGHANA = "par_hr_002";

    /** A well-formed ID that exists in no nonprofit. */
    public static final String NO_SUCH_REQUEST = "req_does_not_exist";

    private Fixtures() {
    }
}
