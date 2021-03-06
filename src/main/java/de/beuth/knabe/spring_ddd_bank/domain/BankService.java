package de.beuth.knabe.spring_ddd_bank.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Service;

import de.beuth.knabe.spring_ddd_bank.domain.imports.AccountAccessRepository;
import de.beuth.knabe.spring_ddd_bank.domain.imports.ClientRepository;
import static multex.MultexUtil.create;

/**This is a domain service for a clerk of a bank.
 * @author Christoph Knabe
 * @since 2017-03-01
 */
@Service
//@Secured("BANK") //Only role BANK may call the methods in this service class. You can apply this annotation at the class or at the method level.
public class BankService {

    //Required repositories as by Ports and Adapters Pattern:
    private final ClientRepository clientRepository;
    private final AccountAccessRepository accountAccessRepository;


    @Autowired
    public BankService(final ClientRepository clientRepository, final AccountAccessRepository accountAccessRepository) {
        this.clientRepository = clientRepository;
        this.accountAccessRepository = accountAccessRepository;
    }

    /**Command: Creates a new bank client.
     * @param username the unique username of the new client. 
     * It must match the regular expression <code>[a-z_A-Z][a-z_A-Z0-9]{0,30}</code>.
     * @param birthDate the birth date of the new client, must not be null
     */
    public Client createClient(final String username, final LocalDate birthDate) {
    	final Pattern pattern = Pattern.compile("[a-z_A-Z][a-z_A-Z0-9]{0,30}");
    	if(!pattern.matcher(username).matches()) {
    		throw create(UsernameExc.class, username);
    	}
        final Client client = clientRepository.save(new Client(username, birthDate));
        return client;
    }
    
    /**Illegal username "{0}". Must have 1..31 characters, start with a letter and contain only english letters, underscores, and decimal digits.*/
    public static class UsernameExc extends multex.Exc {}

    /**Command: Deletes the given {@link Client}.
     * @throws DeleteExc Client has accounts, where he is the owner.*/
    public void deleteClient(final Client client){
        final List<AccountAccess> managedAccounts = accountAccessRepository.findManagedAccountsOf(client, true);
        for(final AccountAccess accountAccess: managedAccounts){
            if(accountAccess.isOwner()){
                throw create(DeleteExc.class, client, accountAccess.getAccount());
            }else{
                accountAccessRepository.delete(accountAccess);
            }
        }
        clientRepository.delete(client);
    }

    /**Cannot delete client {0}, Still owns account {1}.*/
    public static class DeleteExc extends multex.Exc {}

    /**Query: Finds the client with the given id, if exists.
     * @deprecated Use {@link #findClient(String)} for search by username instead. Since 2017-11-13
     */
    public Optional<Client> findClient(final Long id) {
        return clientRepository.find(id);
    }
    /**Query: Finds the client with the given username, if exists.*/
    public Optional<Client> findClient(final String username) {
        return clientRepository.find(username);
    }

    /**Query: Finds all clients of the bank. They are ordered by their descending IDs, that means the newest come first.*/
    public List<Client> findAllClients(){
        return clientRepository.findAll();
    }

    /**Query: Finds all clients of the bank, who are born at the given date or later. They are ordered by their ascending age and secondly by their descending IDs.*/
    public List<Client> findYoungClients(final LocalDate fromBirth){
        return clientRepository.findAllBornFrom(fromBirth);
    }

    /**Query: Finds all clients of the bank, who own or manage an account with the given mimimum balance. They are ordered by their descending account balance and secondly by their descending IDs.*/
    public List<Client> findRichClients(final Amount minBalance){
        final List<AccountAccess> fullAccounts = accountAccessRepository.findFullAccounts(minBalance);
        final Stream<Client> richClients = fullAccounts.stream().map(accountAccess -> accountAccess.getClient()).distinct();
        return richClients.collect(Collectors.toList());
    }

}
