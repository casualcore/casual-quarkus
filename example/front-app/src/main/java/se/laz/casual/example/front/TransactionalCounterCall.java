package se.laz.casual.example.front;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.connection.caller.CasualCaller;

@ApplicationScoped
public class TransactionalCounterCall
{
    private final CasualCaller casualCaller;
    private final TransactionManager transactionManager;

    @Inject
    public TransactionalCounterCall(CasualCaller casualCaller, TransactionManager transactionManager)
    {
        this.casualCaller = casualCaller;
        this.transactionManager = transactionManager;
    }

    @Transactional(value = Transactional.TxType.REQUIRES_NEW, rollbackOn = Exception.class)
    public ServiceReturn<CasualBuffer> call(String serviceName, CasualBuffer buffer) throws SystemException
    {
        ServiceReturn<CasualBuffer> result = casualCaller.tpcall(serviceName, buffer, Flag.of(AtmiFlags.NOFLAG));
        if (result.getErrorState() != ErrorState.OK)
        {
            transactionManager.setRollbackOnly();
        }
        return result;
    }
}
